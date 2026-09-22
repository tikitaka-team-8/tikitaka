# S11-B 인기 회차 좌석 목록 반복조회 부하테스트 (Seat List Load)

대량 데이터 상황에서 인기 회차의 좌석 목록(`GET /api/v1/schedules/{eventSessionId}/seats`)을
여러 사용자가 반복 조회할 때 응답시간·오류율·응답 크기가 어떻게 변하는지 확인하는 부하테스트입니다.

- 주 담당: Seat / 협업: Queue·Infra
- 도구: k6, SQL, Grafana

## Quickstart

**현재 코드 상태: `SeatListReader.PROJECTION_ENABLED = true`, `SeatListCacheConfig.CACHE_ENABLED = true`
(TTL 2초)** — 필드 프로젝션 + 짧은 TTL 캐시가 둘 다 켜져 있습니다. 아래 명령어를 위에서부터
순서대로 그대로 실행하면 지금과 똑같은 조건으로 테스트를 재현할 수 있습니다. (다른 실험 조합을
보고 싶다면 아래 "성능 개선 실험" 절에서 상수를 바꾸는 방법을 참고하세요.)

```bash
# 0) (최초 1회) k6 설치 — 이미 있으면 건너뛰기
brew install k6
# 또는 docker run --rm -i grafana/k6 run - <script.js 형태로 매번 실행해도 됩니다.

# 1) 스택 기동 + 지금 코드(위 두 토글이 true인 상태)로 이미지 재빌드
docker-compose up -d --build

# 2) ticketing-service가 healthy 될 때까지 대기 (STATUS가 healthy로 바뀔 때까지 몇 번 반복 확인)
docker-compose ps ticketing-service

# 3) 인기 회차(31000000-0000-0000-0000-000000000001)에 대량 더미 좌석 시드
psql "host=localhost port=5434 dbname=tikitaka_ticketing user=ticketing" \
  -v session_id="'31000000-0000-0000-0000-000000000001'" \
  -v seat_count=3000 \
  -v available_ratio=0.05 \
  -v held_ratio=0.05 \
  -f scripts/test-scenarios/s11-seat-list-load/seed-seat-list-load.sql

# 4) (최초 1회) 실행 스크립트에 실행 권한 부여 — git에서 실행 비트가 안 딸려올 수 있습니다
chmod +x scripts/test-scenarios/s11-seat-list-load/run-seat-list-load-steps.sh

# 5) k6 로드 테스트 실행 (20 VU 워밍업 → 50 → 100 → 300 → 500 → 1,000 VU), 콘솔 로그도 같이 저장
mkdir -p artifacts/k6/s11-seat-list-load/logs
BASE_URL=http://localhost:8082 scripts/test-scenarios/s11-seat-list-load/run-seat-list-load-steps.sh \
  2>&1 | tee artifacts/k6/s11-seat-list-load/logs/run_$(date +%H%M%S).log

# 6) (테스트가 끝난 뒤) 시드로 만든 더미 좌석 정리
psql "host=localhost port=5434 dbname=tikitaka_ticketing user=ticketing" \
  -v session_id="'31000000-0000-0000-0000-000000000001'" \
  -f scripts/test-scenarios/s11-seat-list-load/cleanup-seat-list-load.sql
```

결과 확인 방법과 PASS 기준은 아래 "확인"/"PASS 기준" 절을 참고하세요.

## 구성 파일

| 파일 | 역할 |
|---|---|
| `seat-list-load.js` | 단계별(50→100→300→500→1,000 VU) 좌석 목록 반복조회 k6 스크립트. `setup()`에서 큐 어드미션 토큰까지 자체 발급함 |
| `run-seat-list-load-steps.sh` | 5단계(50/100/300/500/1000 VU)를 순서대로 실행하는 wrapper 스크립트 |
| `seed-seat-list-load.sql` | 부하테스트용 더미 좌석 데이터 생성 (session_id/seat_count/available_ratio/held_ratio 파라미터화) |
| `cleanup-seat-list-load.sql` | `seed-seat-list-load.sql`로 만든 더미 좌석만 골라 삭제 |
| `seat-list.js` | 단일 VU·단일 요청으로 좌석 목록 API를 빠르게 수동 확인하는 스모크 스크립트 |

## 준비

1. `docker-compose up -d`로 최소한 platform/ticketing 스택(각 DB·Redis 포함) + Prometheus/Grafana를 띄워둡니다. Gateway는 거치지 않고 ticketing-service(기본 `localhost:8082`)에 직접 요청합니다.
2. 인기 회차(`event_session_id=31000000-0000-0000-0000-000000000001`)에 대량 더미 좌석을 생성합니다:
   ```bash
   psql "host=localhost port=5434 dbname=tikitaka_ticketing user=ticketing" \
     -v session_id="'31000000-0000-0000-0000-000000000001'" \
     -v seat_count=3000 \
     -v available_ratio=0.05 \
     -v held_ratio=0.05 \
     -f scripts/test-scenarios/s11-seat-list-load/seed-seat-list-load.sql
   ```
3. k6 설치: `brew install k6` (또는 `docker run --rm -i grafana/k6 run - <script.js`). 큐 토큰 발급은 스크립트가 알아서 하므로 python/pip/venv는 불필요합니다.

## 실행

```bash
BASE_URL=http://localhost:8082 scripts/test-scenarios/s11-seat-list-load/run-seat-list-load-steps.sh
```

- 내부적으로 20 VU 워밍업 → 50 → 100 → 300 → 500 → 1,000 VU를 순서대로 실행합니다 (기본 ramp 20s / 유지 1m / ramp-down 20s, 총 1분40초/단계).
- ⚠️ **admission-token-ttl(기본 3분=180s)보다 한 단계(mint + ramp + 유지 + ramp-down)가 길면 막판 요청이 토큰 만료(Q-001)로 대량 실패합니다.** 실측으로 확인된 문제로, 기본값(1분40초)은 이 TTL보다 충분히 짧게 잡아뒀습니다. 임의로 `LOAD_RAMP`/`LOAD_DURATION`을 늘릴 경우 이 여유를 꼭 다시 계산하세요.
- 한 단계에서 threshold(p95<500ms, 오류율<1%)를 넘어도 스크립트는 죽지 않고 다음 단계로 계속 진행합니다 (`run-seat-list-load-steps.sh`가 k6의 비정상 종료 코드를 무시하도록 되어 있음).
- 결과 JSON은 이 폴더가 아니라 `artifacts/k6/s11-seat-list-load/results/<타임스탬프>/`에 쌓입니다. 원본 콘솔 로그를 남기고 싶으면 `... run-seat-list-load-steps.sh 2>&1 | tee artifacts/k6/s11-seat-list-load/logs/run_$(date +%H%M%S).log`처럼 직접 리다이렉트하세요.
- 단일 요청만 빠르게 확인하고 싶다면: `k6 run --env SESSION_ID=<id> --env QUEUE_TOKEN=<token> scripts/test-scenarios/s11-seat-list-load/seat-list.js`

## 확인 (API, DB, Grafana)

- **응답시간/오류율/응답 크기**: k6 종료 시 출력되는 요약(`http_req_duration` p95/p99, `http_req_failed`, 커스텀 지표 `seat_response_size`)을 그대로 읽으면 됩니다. `seat_response_size`가 갑자기 커져 있다면(수백 KB) 페이지네이션이 적용되지 않은 응답을 받고 있다는 신호이니, ticketing-service가 최신 코드로 재빌드·재기동됐는지부터 확인하세요 (Dockerfile이 빌드 시점의 `src`를 이미지에 굽기 때문에, 소스만 고치고 컨테이너를 재빌드하지 않으면 예전 동작이 그대로 남습니다).
- **DB 커넥션 풀**: Grafana에서 `hikaricp_connections_active`, `hikaricp_connections_pending`, `hikaricp_connections_timeout_total{job="ticketing-service"}` 확인.
- **CPU/JVM Heap**: Grafana `TIKITAKA Load Test - Service` 대시보드에서 테스트 시간대로 범위를 맞춰 확인.
- **부하 종료 후 정상화**: wrapper 스크립트가 각 단계 종료 후 `pg_stat_activity` 활성 커넥션 수를 출력합니다 — 평시 수준으로 내려오는지 확인.

## PASS 기준

- 모든 단계에서 `http_req_duration p(95) < 500ms` 이고 `http_req_failed rate < 1%`.
- `seat_response_size`가 요청한 `size`(기본 50)에 해당하는 수준(수 KB)으로 유지된다 — 수백 KB로 커지면 페이지네이션 미적용을 의심하고 FAIL로 취급.
- Q-001(대기열 입장 권한 없음) 예외가 대량 발생하지 않는다 — 발생한다면 서버 성능 문제가 아니라 admission-token-ttl 대비 테스트 단계 길이 설정 문제일 가능성이 높음 (`docs/test-results/S11-seat-list-load/local-result.md` 참고).

## 정리

- `seed-seat-list-load.sql`로 만든 더미 좌석은 아래로 정리합니다:
  ```bash
  psql "host=localhost port=5434 dbname=tikitaka_ticketing user=ticketing" \
    -v session_id="'31000000-0000-0000-0000-000000000001'" \
    -f scripts/test-scenarios/s11-seat-list-load/cleanup-seat-list-load.sql
  ```
- `artifacts/k6/s11-seat-list-load/` 아래 결과 JSON·로그·토큰 파일은 Git에 올리지 않는 원본 자료입니다 (용량이 크면 주기적으로 정리해도 됩니다). 결과를 팀에 공유할 때는 요약을 `docs/test-results/S11-seat-list-load/local-result.md`에 반영하세요.

## 성능 개선 실험: 필드 프로젝션 / 짧은 TTL 캐시

페이지네이션 적용 이후 추가로 검증해볼 두 가지 개선안을, 서버 코드에 토글로 구현해뒀습니다.
application.yaml 설정이 아니라 **코드 안의 상수**로 켜고 끕니다. 코드 최초 작성 시 기본값은 둘 다
꺼져 있었지만(`false`), **지금 저장소 상태는 위 "지금 상태 그대로 재현하기" 절에 적힌 대로 둘 다
`true`로 켜져 있습니다** — 아래 표의 "기본값"은 이 실험 자체를 처음 설계했을 때 기준이고, 실제로
지금 코드에 어떤 값이 들어있는지는 항상 파일을 직접 열어 확인하세요.

| 상수 | 위치 | 기본값 | 의미 |
|---|---|---|---|
| `PROJECTION_ENABLED` | `SeatListReader` | `false` | `true`면 `ScheduleSeat` 엔티티 전체(감사 컬럼 포함 15개) 대신, 응답에 실제 필요한 7개 컬럼만 JPQL 생성자 표현식으로 SELECT합니다. |
| `CACHE_ENABLED` | `SeatListCacheConfig` | `false` | `true`면 동일한 `(eventSessionId, section, grade, page, size)` 조합 조회 결과를 `CACHE_TTL_SECONDS` 동안 캐싱합니다(Caffeine, in-memory). |
| `CACHE_TTL_SECONDS` | `SeatListCacheConfig` | `2` | 캐시를 켰을 때의 TTL(초). |

두 클래스 모두 `ticketing-service/src/main/java/com/tikitaka/ticketing/seat/` 아래에 있습니다
(`application/service/SeatListReader.java`, `config/SeatListCacheConfig.java`).

### 실행 방법 (한 번에 하나씩만 바꿔서 비교)

기존 방식과 동일하게, 상수 하나만 바꾸고 재빌드·재기동한 뒤 **같은 VU 단계**로 다시 돌려서
바로 직전 결과와 비교하세요.

1. **베이스라인(현재 상태, 둘 다 off)**으로 먼저 한 번 실행해 기준값을 기록합니다.
   ```bash
   BASE_URL=http://localhost:8082 scripts/test-scenarios/s11-seat-list-load/run-seat-list-load-steps.sh
   ```
2. 실험하고 싶은 상수 **하나만** 바꿉니다. 예: `SeatListReader.java`
   ```java
   private static final boolean PROJECTION_ENABLED = true;   // 필드 프로젝션만 먼저 켜본다
   ```
3. 이미지를 재빌드하고 컨테이너를 재기동합니다 (Dockerfile이 빌드 시점 `src`를 굽기 때문에, 코드만
   고치고 재빌드하지 않으면 이전 동작이 그대로 남습니다 — 위 "확인" 절에서와 동일한 주의사항).
   ```bash
   docker-compose up -d --build ticketing-service
   # healthcheck가 healthy로 바뀔 때까지 대기 (최대 약 40s+10s*n)
   docker-compose ps ticketing-service
   ```
4. 같은 단계로 다시 실행하고 결과를 비교합니다.
   ```bash
   BASE_URL=http://localhost:8082 scripts/test-scenarios/s11-seat-list-load/run-seat-list-load-steps.sh
   ```
5. 비교가 끝나면 `PROJECTION_ENABLED`는 그대로 두고 `SeatListCacheConfig.CACHE_ENABLED = true`만
   추가로 켜서 2~4를 반복하면, "프로젝션만", "캐시만", "둘 다"의 3가지 조합을 순서대로 비교할 수
   있습니다.

### 확인 포인트

- **응답시간(p95/p99)**: `http_req_duration`이 베이스라인 대비 얼마나 줄었는지.
- **DB 부하**: Grafana `hikaricp_connections_active` / `hikaricp_connections_pending`이 프로젝션 on일 때
  줄어드는지(하이드레이션할 컬럼이 적어 쿼리·GC 비용이 낮아지는 효과를 기대).
- **캐시 적중 여부**: 캐시를 켰을 때 반복 조회 구간에서 `http_req_duration`이 눈에 띄게 더 낮아지는지 —
  TTL(기본 2초)이 짧기 때문에, 동일 페이지를 짧은 간격으로 반복 조회하는 VU 비율이 낮으면 효과가
  미미하게 나올 수 있습니다(이 경우 TTL을 일시적으로 늘려서 캐시 자체의 효과만 분리 검증해볼 수 있음).
- **정합성**: 좌석 상태가 실시간으로 바뀌는 환경(선점/해제)에서는 캐시가 짧은 시간 동안 stale한
  좌석 상태를 보여줄 수 있다는 트레이드오프가 있습니다 — TTL을 얼마나 짧게 가져갈지는 이 트레이드오프와
  성능 개선 폭을 같이 보고 판단하세요.

결과는 `docs/test-results/S11-seat-list-load/local-result.md`에 실험 조합별로 기록하세요.

