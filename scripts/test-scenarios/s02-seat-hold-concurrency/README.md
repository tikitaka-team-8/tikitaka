# S02 좌석 동시 선점 경쟁 테스트 (Seat Hold Race)

인기 공연 A-01 좌석에 여러 사용자가 동시에 Seat Hold를 요청했을 때, 정확히 한 명만 선점에
성공하고 최종 데이터가 일관되는지 확인하는 부하/정합성 테스트입니다.

- 주 담당: Seat·Seat Hold / 협업: Queue·Infra
- 도구: k6, SQL, Grafana

## 구성 파일

| 파일 | 역할 |
|---|---|
| `seat-concurrency.js` | 동일 좌석에 대한 동시 Seat Hold 요청 → 승자/충돌 판정까지만 검증하는 k6 스크립트 (예매·결제·Kafka 이벤트 처리는 범위 밖) |
| `reset-seat-hold-race.sql` | 다음 동시성 단계로 넘어가기 전 좌석/선점/예매 상태 초기화 |
| `verify-seat-hold-race.sql` | 테스트 종료 후 최종 데이터 정합성 확인 |

## 준비

1. `docker-compose up -d platform-postgres platform-redis platform-service ticketing-postgres ticketing-redis ticketing-service prometheus grafana`
   (이 스크립트는 좌석 락 동시성만 검증하므로 예매·결제·Kafka 쪽 컨테이너(`payment-postgres`,
   `payment-notification-service`, `kafka`)는 필요 없습니다. Gateway/JWT도 거치지 않고 ticketing-service에
   `X-User-Id` 헤더로 직접 요청합니다 — 이번 테스트의 관심사는 좌석 동시성이지 인증 계층이 아니기 때문입니다.
   **다만 platform-service는 빼면 안 됩니다**: 대기열 진입(`POST .../queue`)이 내부적으로
   `PlatformSalesStatusClient`로 platform-service의 `/api/v1/internal/event-sessions/{sessionId}/sales-status`를
   Feign 호출해서 판매 기간·`queue_enabled`를 확인하기 때문에, platform-service(및 platform-postgres/platform-redis)가
   떠 있지 않으면 대기열 진입 자체가 `C-003 DOWNSTREAM_SERVICE_FAILURE`(502)로 실패합니다. Gateway까지 포함해서
   재려면 아래 "Gateway를 포함하려면" 참고.)
2. 시드 데이터 2종을 각각의 DB에 적용합니다 (하나라도 빠지면 sales-status 조회나 좌석 조회가 실패합니다).
   로컬에 `psql` 클라이언트를 따로 설치할 필요 없이, 이미 떠 있는 `platform-postgres`/`ticketing-postgres`
   컨테이너 안에서 바로 실행합니다(SQL 파일은 호스트에만 있으므로 `-f` 대신 표준입력으로 흘려보냅니다).
   ```bash
   set -a; source .env; set +a

   docker exec -i -e "PGPASSWORD=${PLATFORM_DB_PASSWORD}" tikitaka-platform-postgres \
     psql -U "${PLATFORM_DB_USERNAME:-platform}" -d "${PLATFORM_DB_NAME:-tikitaka_platform}" -v ON_ERROR_STOP=1 \
     < scripts/integration-test/seed/platform-seed.sql

   docker exec -i -e "PGPASSWORD=${TICKETING_DB_PASSWORD}" tikitaka-ticketing-postgres \
     psql -U "${TICKETING_DB_USERNAME:-ticketing}" -d "${TICKETING_DB_NAME:-tikitaka_ticketing}" -v ON_ERROR_STOP=1 \
     < scripts/integration-test/seed/ticketing-seed.sql
   ```
   (컨테이너 이름이 다르면 `docker ps`로 확인해서 `tikitaka-platform-postgres`/`tikitaka-ticketing-postgres`
   부분만 바꾸세요.) `platform-seed.sql`은 `event_session_id=31000000-...-0001`의 판매 기간을 매번
   `CURRENT_TIMESTAMP` 기준 상대 시간으로 넣으므로(판매 시작 -1시간 ~ 마감 +2시간, `queue_enabled=TRUE`),
   테스트 직전에 다시 적용해도 안전합니다. `ticketing-seed.sql`의 기본값으로 쓰는
   `SEAT_ID`(`schedule_seat_id=40000000-...-0001`)가 같은 세션의 "VIP A-1" 좌석입니다.
3. k6 설치: `brew install k6` (또는 `docker run --rm -i grafana/k6 run - <script.js`)

## 실행 — 10 → 50 → 100 → (필요 시) 300 단계

각 단계 전에 좌석을 리셋하고, 단계별로 스크립트를 실행합니다.

로컬에 `psql` 클라이언트를 따로 설치하지 않고, 이미 떠 있는 `ticketing-postgres` 컨테이너 안에서 바로 실행합니다
(SQL 파일은 호스트에만 있으므로 `-f` 대신 표준입력으로 흘려보냅니다).

```bash
set -a; source .env; set +a   # TICKETING_DB_NAME/USERNAME/PASSWORD 로드

# 문자열 변수로 만들면 zsh에서는 단어 분리가 안 돼 "command not found"가 나므로 배열로 만든다
PSQL=(docker exec -i -e "PGPASSWORD=${TICKETING_DB_PASSWORD}" tikitaka-ticketing-postgres \
  psql -U "${TICKETING_DB_USERNAME:-ticketing}" -d "${TICKETING_DB_NAME:-tikitaka_ticketing}" -v ON_ERROR_STOP=1)
SEAT_ID="40000000-0000-0000-0000-000000000001"
SESSION_ID="31000000-0000-0000-0000-000000000001"

# 10명
"${PSQL[@]}" -v seat_id="$SEAT_ID" -v session_id="$SESSION_ID" < scripts/test-scenarios/s02-seat-hold-concurrency/reset-seat-hold-race.sql
k6 run --env VUS=10 scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js

# 50명
"${PSQL[@]}" -v seat_id="$SEAT_ID" -v session_id="$SESSION_ID" < scripts/test-scenarios/s02-seat-hold-concurrency/reset-seat-hold-race.sql
k6 run --env VUS=50 scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js

# 100명
"${PSQL[@]}" -v seat_id="$SEAT_ID" -v session_id="$SESSION_ID" < scripts/test-scenarios/s02-seat-hold-concurrency/reset-seat-hold-race.sql
k6 run --env VUS=100 scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js

# (필요한 경우에만) 300명
"${PSQL[@]}" -v seat_id="$SEAT_ID" -v session_id="$SESSION_ID" < scripts/test-scenarios/s02-seat-hold-concurrency/reset-seat-hold-race.sql
k6 run --env VUS=300 scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js
```

`docker exec` + `PGPASSWORD`로 접속하므로 별도 비밀번호 입력 프롬프트는 뜨지 않습니다. (컨테이너 이름이 다르면 `docker ps`로 확인해서 `tikitaka-ticketing-postgres` 부분만 바꾸세요.)

각 단계 실행 후:

```bash
"${PSQL[@]}" -v seat_id="$SEAT_ID" < scripts/test-scenarios/s02-seat-hold-concurrency/verify-seat-hold-race.sql
```

## 확인 (API, DB, Redis, Kafka, Grafana)

- **성공·충돌·예상하지 못한 실패·Timeout 수**: k6 실행 종료 시 출력되는 커스텀 카운터를 그대로 읽으면 됩니다.
  - `seat_hold_success` — 정확히 1이어야 합니다.
  - `seat_hold_conflict` — 나머지 인원 수와 같아야 합니다 (동일하고 설명 가능한 충돌 = 409 + `code: S-003`).
  - `seat_hold_unexpected` / `seat_hold_timeout` — 0이 아니면 원인 조사가 필요합니다.
- **Seat API p95·p99**: k6 쪽은 `seat_hold_duration` 지표(스크립트 안 `thresholds`로 이미 체크됨).
  서버 쪽 실측치는 Grafana에서 Prometheus 데이터소스로 아래 PromQL을 패널에 추가해 확인하세요
  (Actuator가 경로를 템플릿째로 라벨링하므로 UUID를 몰라도 필터링됩니다):
  ```promql
  histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{
    job="ticketing-service", uri="/api/v1/schedules/{eventSessionId}/seats/{scheduleSeatId}/hold"
  }[1m])) by (le))
  ```
- **DB Lock Wait / Connection Pool / 트랜잭션 롤백·데드락**:
  - Connection Pool: Grafana에서 `hikaricp_connections_active`, `hikaricp_connections_pending`,
    `hikaricp_connections_timeout_total{job="ticketing-service"}` 확인 (pending이 계속 쌓이면 커넥션 풀 부족).
  - Lock Wait: 테스트 실행 "중"에 별도 터미널에서 반복 조회
    ```sql
    SELECT pid, wait_event_type, wait_event, query, query_start
    FROM pg_stat_activity
    WHERE datname = current_database() AND wait_event_type = 'Lock';
    ```
  - 데드락/롤백 누적치: 테스트 전후로 diff
    ```sql
    SELECT deadlocks, xact_rollback FROM pg_stat_database WHERE datname = current_database();
    ```
- **최종 Seat Hold / Seat 상태**: `verify-seat-hold-race.sql` 결과 그대로 (좌석 `HELD` 1건,
  SeatHold는 `HOLDING` 1건 — 승자가 선점한 상태 그대로 남아있어야 합니다). 이 스크립트는 예매·결제로는
  진행하지 않으므로 Reservation은 생성되지 않는 게 정상입니다(별도 확인 불필요).

## PASS 기준

- `seat_hold_success == 1`이고 나머지 전원이 `seat_hold_conflict`(409 + `S-003`)로 설명 가능하게 실패한다.
- `seat_hold_unexpected` / `seat_hold_timeout`이 모두 0이다.
- `verify-seat-hold-race.sql` 기준 최종 상태가 Seat `HELD` 1건, SeatHold `HOLDING` 1건으로 일치한다(예매/결제는 이 테스트 범위 밖이라 Reservation은 생성되지 않는 것이 정상).
- 데드락·트랜잭션 롤백이 비정상적으로 누적되지 않는다.

## 정리

- 다음 실행 전 `reset-seat-hold-race.sql`로 좌석·선점·예매 상태를 원복한다 (위 실행 스니펫에 포함됨).
- 이 테스트는 Gateway를 거치지 않고 `X-User-Id`로 직접 요청하므로 회원가입으로 생긴 임시 사용자 데이터가 없다 — 별도 사용자 정리는 불필요하다.

## Gateway를 포함하려면

Gateway(JWT 인증)까지 포함해서 재고 싶다면, k6 스크립트 안의 `X-User-Id` 직접 헤더 대신
`scripts/integration-test/run.sh`처럼 회원가입 → 로그인 → `Authorization: Bearer` 흐름을
`setup()`에 추가해야 합니다. VU 수만큼 실제 회원가입 API를 호출해야 해서 300명 규모에서는
그 자체로 부하가 커지니, "좌석 동시성"과 "인증 트래픽"을 분리해서 보고 싶다면 지금 구성(Gateway
우회)을 그대로 쓰는 걸 권장합니다.
