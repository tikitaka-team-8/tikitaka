# S08 Kafka·Consumer 장애 복구 테스트 가이드

이 디렉터리는 S08 Kafka 장애·복구 시나리오의 Seed, 장애 주입, 부하 실행, 검증과 정리 자료를 함께 관리한다. 이 문서는 다른 팀원이 테스트 방법과 PASS 기준을 재현하기 위한 가이드이며, 특정 코드 버전에서 측정한 실행 결과는 [`docs/test-results/S08-kafka-recovery/local-result.md`](../../../docs/test-results/S08-kafka-recovery/local-result.md)에 기록한다.

## 디렉터리 구성

```text
scripts/test-scenarios/s08-kafka-recovery/
├── README.md
├── seed/
│   ├── 01-consumer-recovery/
│   ├── 02-retry-dlt-comparison/
│   └── csv/
├── inject/
│   ├── 02-retry-dlt-comparison/
│   ├── 03-retry-success/
│   └── inject-*.sh
├── load/
│   ├── kafka-*.js
│   └── run-*.sh
├── verify/
│   ├── common/
│   └── 03-retry-success/
└── cleanup/
    ├── cleanup-fixture-*.sql
    └── cleanup-fault-*.sql
```

- `seed`: Consumer 복구와 Retry·DLT 비교에 사용하는 Fixture
- `inject`: Kafka 실패 이벤트와 재시도 가능한 DB 오류 주입
- `load`: k6 부하, CSV와 장애 주입 조합 실행
- `verify`: 공통 최종 상태와 재시도 성공 결과 검증
- `cleanup`: Fixture와 실패 주입 객체 정리

`cleanup-fault-*.sql`은 트리거·함수·시퀀스만 제거하고, `cleanup-fixture-*.sql`은 01과 02가 공유하는 S08 전용 데이터 범위를 제거한다. 실패 주입 객체를 먼저 제거한 뒤 Fixture를 Payment·Notification → Ticketing → Platform 순서로 정리한다.

## 전체 테스트 구성

```text
SQL Fixture 준비
    ↓
Reservation·Notification Listener 중단
    ↓
실행 Shell
 ├─ k6 정상 결제 승인 1,000건
 └─ Kafka 실패 이벤트 10건 동시 주입
    ↓
Ticketing Listener 복구 → Retry·DLT·Reservation 상태 검증
    ↓
Notification Listener 복구 → Retry·DLT·Notification 상태 검증
    ↓
SQL·Kafka UI·서비스 로그·Grafana 최종 교차 검증
```

- k6는 정상 API 요청의 성공률·처리량과 응답시간을 측정한다.
- Kafka Shell은 k6 부하 구간에 실패 이벤트를 분산 주입하고 두 작업의 완료를 조율한다.
- SQL Seed는 도메인 초기 상태를 만들고, SQL Trigger는 `SQLSTATE 40001`로 재시도 가능한 DB 오류를 재현한다.
- SQL Verify, Kafka UI·CLI, 서비스 로그와 Grafana는 Lag·Offset·DLT·최종 데이터·자원 사용량을 교차 확인한다.
- JUnit 단위 테스트와 Embedded Kafka·PostgreSQL Testcontainers 통합 테스트는 Retry·DLT 정책, 멱등성과 DLT 원본 보존을 자동 검증한다.

## 공통 준비

### 환경

- 저장소 루트에서 PowerShell 명령 실행
- Kafka 주입과 조합 실행 Shell은 Git Bash에서 실행
- Docker Compose의 Kafka, PostgreSQL, Gateway와 각 서비스 기동
- 실제 비밀번호·JWT·Service Key를 저장소 파일에 기록하지 않음

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --wait --wait-timeout 120
```

Platform Fixture의 비밀번호 해시는 로그인에 사용하지 않는 테스트 전용 값으로 실행 셸에만 설정한다.

```powershell
$env:S08_PASSWORD_HASH = "fixture-user-not-for-login"
```

### Consumer 중단

애플리케이션 전체를 중단하지 않고 Reservation과 Notification Listener만 중단한다.

```powershell
$env:RESERVATION_KAFKA_CONSUMER_AUTO_STARTUP = "false"
$env:NOTIFICATION_KAFKA_CONSUMER_AUTO_STARTUP = "false"

docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --no-deps --force-recreate ticketing-service payment-notification-service
```

Kafka UI 또는 Consumer Group 명령으로 다음 초기 상태를 확인한다.

- `ticketing-payment-group`: Member 0
- `notification-reservation-group`: Member 0
- 테스트 시작 전 각 Group의 Lag 기록
- 기존 DLT가 있으면 절대 건수와 실행 후 증가량을 함께 기록

### 공통 검증 SQL

Ticketing:

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\verify\common\verify-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Payment·Notification:

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\verify\common\verify-payment-notification.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 01. Consumer 중단과 복구

### 목적

Listener 중단 중에도 Payment와 Reservation 이벤트가 Kafka에 보존되고, 복구 후 정상 이벤트 1,000건이 중복 없이 최종 상태까지 처리되는지 확인한다.

### 테스트 자료

- k6: `scripts/test-scenarios/s08-kafka-recovery/load/kafka-recovery.js`
- 사용자: `scripts/test-scenarios/s08-kafka-recovery/seed/csv/s08-users.csv`
- 결제: `scripts/test-scenarios/s08-kafka-recovery/seed/csv/s08-payment-approvals.csv`
- SQL: `seed/01-consumer-recovery/`

### Fixture 준비

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\seed\01-consumer-recovery\seed-platform.sql |
    docker exec -i -e S08_PASSWORD_HASH=$env:S08_PASSWORD_HASH tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -v password_hash="$S08_PASSWORD_HASH" -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\seed\01-consumer-recovery\seed-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\seed\01-consumer-recovery\seed-payment-notification.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

공통 검증 SQL로 Payment 1,000건이 `READY`, Reservation 1,000건이 `PAYMENT_PROCESSING`, SeatHold 1,000건이 `RESERVED`, ScheduleSeat 1,000건이 `HELD`이고 Inbox·Outbox·Notification이 0건인지 확인한다.

### 부하와 복구

```powershell
k6 run .\scripts\test-scenarios\s08-kafka-recovery\load\kafka-recovery.js
```

부하 완료 후 `payment-events` Lag 증가를 확인하고 Ticketing Listener를 복구한다.

```powershell
$env:RESERVATION_KAFKA_CONSUMER_AUTO_STARTUP = "true"
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --no-deps --force-recreate ticketing-service
```

`ticketing-payment-group` Lag가 0이 되고 `reservation-events` Lag가 증가한 뒤 Notification Listener를 복구한다.

```powershell
$env:NOTIFICATION_KAFKA_CONSUMER_AUTO_STARTUP = "true"
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --no-deps --force-recreate payment-notification-service
```

### PASS 기준

- 결제 승인 1,000건 성공, HTTP 오류율 1% 미만, p95 2초 미만
- 두 Consumer의 최종 Lag 0
- Payment·Reservation·Seat·Notification 각 1,000건 최종 상태 일치
- Inbox·Outbox의 이벤트 및 도메인 식별자 중복 0건
- Consumer 복구 시각과 Lag 정상화 시간 기록

### Fixture 정리

Payment·Notification → Ticketing → Platform 순서로 실행한다.

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fixture-payment-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fixture-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fixture-platform.sql | docker exec -i tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 02. Retry·DLT 적용 전후 비교

### 목적과 비교 조건

정상 결제 승인 1,000건과 실패 이벤트 10건을 함께 발생시키고 다음을 비교한다.

- Baseline: 예외 유형과 무관하게 이벤트당 총 10회 처리, DLT 없음
- 개선 후: 비재시도 5건은 즉시 DLT, 재시도 가능 5건은 2초 간격으로 최초 포함 총 3회 후 DLT

Baseline은 결과 문서의 Baseline Commit에서 측정한 결과다. 현재 개선 코드의 재검증은 아래 개선 후 절차를 사용한다.

### 테스트 자료

- k6: `scripts/test-scenarios/s08-kafka-recovery/load/kafka-failure-recovery.js`
- 사용자: `scripts/test-scenarios/s08-kafka-recovery/seed/csv/s08-failure-users.csv`
- 결제: `scripts/test-scenarios/s08-kafka-recovery/seed/csv/s08-failure-payment-approvals.csv`
- 실행: `scripts/test-scenarios/s08-kafka-recovery/load/`
- Kafka 주입: `scripts/test-scenarios/s08-kafka-recovery/inject/`
- 공통 Fixture: `seed/02-retry-dlt-comparison/`
- 재시도 오류 주입: `inject/02-retry-dlt-comparison/`

### Fixture와 실패 트리거 준비

01 테스트와 같은 순서로 Listener를 중단한 뒤 실행한다.

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\seed\02-retry-dlt-comparison\seed-platform.sql | docker exec -i -e S08_PASSWORD_HASH=$env:S08_PASSWORD_HASH tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -v password_hash="$S08_PASSWORD_HASH" -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\seed\02-retry-dlt-comparison\seed-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\seed\02-retry-dlt-comparison\seed-payment-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\inject\02-retry-dlt-comparison\setup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\inject\02-retry-dlt-comparison\setup-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

공통 검증 SQL로 초기 상태와 중복 0건을 확인한다.

### 혼합 부하와 복구

Git Bash에서 정상 승인 1,000건과 Payment 실패 이벤트 10건을 동시에 실행한다.

```bash
./scripts/test-scenarios/s08-kafka-recovery/load/run-payment-retry-dlt-load.sh
```

확인 후 Ticketing Listener를 복구하며, Ticketing이 정상 Reservation Outbox를 발행하는 동안 별도 Git Bash에서 다음을 실행한다.

```bash
./scripts/test-scenarios/s08-kafka-recovery/load/run-reservation-retry-dlt-load.sh
```

Ticketing 처리 완료 후 Notification Listener를 복구한다. Listener 복구 명령은 01 테스트와 동일하다.

### PASS 기준

- 정상 결제 승인 1,000건 성공
- Consumer별 비재시도 이벤트 5건은 최초 실패 후 즉시 DLT
- Consumer별 재시도 가능 이벤트 5건은 약 2초 간격, 최초 포함 총 3회 후 DLT
- Consumer별 DLT 증가량 10건
- 실패 Offset 뒤에 후속 정상 Offset이 존재
- 두 Consumer의 최종 Lag 0
- 정상 도메인 데이터 1,000건, Inbox·Outbox 중복 0건
- Baseline과 개선 후 총 처리 시도, 실패 처리 시간과 DLT 보존 결과 비교

### 정리

먼저 실패 트리거를 제거한 뒤 Fixture를 제거한다.

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fault-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fault-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fixture-payment-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fixture-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fixture-platform.sql | docker exec -i tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 03. 재시도 후 최종 성공

### 목적과 선행 조건

재시도 가능한 DB 오류가 최초 두 번 발생하고 해소됐을 때 세 번째 처리에서 정상 성공하는지 검증한다. 02 테스트의 정상 처리 1,000건 Fixture가 남아 있고 두 Consumer가 Member 1, Lag 0인 상태에서 실행한다.

### 준비와 실행

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\inject\03-retry-success\setup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\inject\03-retry-success\setup-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Git Bash에서 단일 Payment 성공 이벤트를 발행한다.

```bash
./scripts/test-scenarios/s08-kafka-recovery/inject/inject-retry-success-payment-event.sh
```

### 결과 검증

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\verify\03-retry-success\verify-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\verify\03-retry-success\verify-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

### PASS 기준

- Ticketing과 Notification 모두 약 2초 간격으로 두 번 실패한 뒤 세 번째 처리 성공
- 각 시도 카운터 3
- Reservation `CONFIRMED`, SeatHold `CONFIRMED`, ScheduleSeat `SOLD`
- Reservation Inbox·Outbox와 Notification·Notification Inbox 각 1건, 중복 0건
- 테스트 전후 DLT 건수 증가 없음
- 두 Consumer의 최종 Lag 0

### 실패 주입 정리

```powershell
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fault-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\test-scenarios\s08-kafka-recovery\cleanup\cleanup-fault-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

공통 실패 주입 cleanup은 영구 실패와 일시적 실패 트리거·함수·시퀀스를 함께 제거한다. 전체 Fixture까지 제거하려면 `cleanup/cleanup-fixture-*.sql`을 Payment·Notification → Ticketing → Platform 순서로 추가 실행한다.
