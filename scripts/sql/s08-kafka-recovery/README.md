# S08 Kafka·Consumer 장애 복구 테스트 가이드

이 디렉터리는 S08 Kafka 장애·복구 시나리오의 SQL을 테스트 단계별로 구분한다. 이 문서는 다른 팀원이 테스트 방법과 PASS 기준을 재현하기 위한 가이드이며, 특정 코드 버전에서 측정한 실행 결과는 [`docs/test-results/S08-kafka-recovery/local-result.md`](../../../docs/test-results/S08-kafka-recovery/local-result.md)에 기록한다.

## 디렉터리 구성

```text
scripts/sql/s08-kafka-recovery/
├── 01-consumer-recovery/
│   ├── seed-*.sql
│   └── cleanup-*.sql
├── 02-retry-dlt-comparison/
│   ├── fixture/
│   │   ├── seed-*.sql
│   │   └── cleanup-*.sql
│   └── fault-injection/
│       ├── setup-*.sql
│       └── cleanup-*.sql
├── 03-retry-success/
│   ├── setup-*.sql
│   ├── verify-*.sql
│   └── cleanup-*.sql
└── _shared/
    └── verify-*.sql
```

- `01-consumer-recovery`: Listener 중단 중 이벤트 보존과 복구 후 정상 처리 검증
- `02-retry-dlt-comparison/fixture`: Baseline과 개선 후 비교에서 함께 사용하는 1,000건 Fixture
- `02-retry-dlt-comparison/fault-injection`: 재시도 가능한 DB 오류를 지속 발생시키는 트리거
- `03-retry-success`: DB 오류가 두 번 발생한 뒤 세 번째 처리에서 해소되는 단일 이벤트 검증
- `_shared`: 1,000건 처리 전후에 공통으로 사용하는 최종 상태 검증

`fault-injection/cleanup-*.sql`과 `03-retry-success/cleanup-*.sql`은 트리거·함수·시퀀스만 제거한다. 실제 Fixture 데이터는 각 테스트의 `cleanup-platform.sql`, `cleanup-ticketing.sql`, `cleanup-payment-notification.sql`로 제거한다.

## 공통 준비

### 환경

- 저장소 루트에서 PowerShell 명령 실행
- Kafka 주입과 조합 실행 Shell은 Git Bash에서 실행
- Docker Compose의 Kafka, PostgreSQL, Gateway와 각 서비스 기동
- 실제 비밀번호·JWT·Service Key를 저장소 파일에 기록하지 않음

```powershell
docker compose config --quiet
docker compose up -d --wait --wait-timeout 120
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

docker compose up -d --no-deps --force-recreate ticketing-service payment-notification-service
```

Kafka UI 또는 Consumer Group 명령으로 다음 초기 상태를 확인한다.

- `ticketing-payment-group`: Member 0
- `notification-reservation-group`: Member 0
- 테스트 시작 전 각 Group의 Lag 기록
- 기존 DLT가 있으면 절대 건수와 실행 후 증가량을 함께 기록

### 공통 검증 SQL

Ticketing:

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\_shared\verify-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Payment·Notification:

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\_shared\verify-payment-notification.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 01. Consumer 중단과 복구

### 목적

Listener 중단 중에도 Payment와 Reservation 이벤트가 Kafka에 보존되고, 복구 후 정상 이벤트 1,000건이 중복 없이 최종 상태까지 처리되는지 확인한다.

### 테스트 자료

- k6: `scripts/k6/kafka-recovery.js`
- 사용자: `scripts/k6/data/s08-users.csv`
- 결제: `scripts/k6/data/s08-payment-approvals.csv`
- SQL: `01-consumer-recovery/`

### Fixture 준비

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\01-consumer-recovery\seed-platform.sql |
    docker exec -i -e S08_PASSWORD_HASH=$env:S08_PASSWORD_HASH tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -v password_hash="$S08_PASSWORD_HASH" -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\01-consumer-recovery\seed-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\01-consumer-recovery\seed-payment-notification.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

공통 검증 SQL로 Payment 1,000건이 `READY`, Reservation 1,000건이 `PAYMENT_PROCESSING`, SeatHold 1,000건이 `RESERVED`, ScheduleSeat 1,000건이 `HELD`이고 Inbox·Outbox·Notification이 0건인지 확인한다.

### 부하와 복구

```powershell
k6 run .\scripts\k6\kafka-recovery.js
```

부하 완료 후 `payment-events` Lag 증가를 확인하고 Ticketing Listener를 복구한다.

```powershell
$env:RESERVATION_KAFKA_CONSUMER_AUTO_STARTUP = "true"
docker compose up -d --no-deps --force-recreate ticketing-service
```

`ticketing-payment-group` Lag가 0이 되고 `reservation-events` Lag가 증가한 뒤 Notification Listener를 복구한다.

```powershell
$env:NOTIFICATION_KAFKA_CONSUMER_AUTO_STARTUP = "true"
docker compose up -d --no-deps --force-recreate payment-notification-service
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
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\01-consumer-recovery\cleanup-payment-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\01-consumer-recovery\cleanup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\01-consumer-recovery\cleanup-platform.sql | docker exec -i tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 02. Retry·DLT 적용 전후 비교

### 목적과 비교 조건

정상 결제 승인 1,000건과 실패 이벤트 10건을 함께 발생시키고 다음을 비교한다.

- Baseline: 예외 유형과 무관하게 이벤트당 총 10회 처리, DLT 없음
- 개선 후: 비재시도 5건은 즉시 DLT, 재시도 가능 5건은 2초 간격으로 최초 포함 총 3회 후 DLT

Baseline은 결과 문서의 Baseline Commit에서 측정한 결과다. 현재 개선 코드의 재검증은 아래 개선 후 절차를 사용한다.

### 테스트 자료

- k6: `scripts/k6/kafka-failure-recovery.js`
- 사용자: `scripts/k6/data/s08-failure-users.csv`
- 결제: `scripts/k6/data/s08-failure-payment-approvals.csv`
- 실행·주입: `scripts/kafka/s08/`
- 공통 Fixture: `02-retry-dlt-comparison/fixture/`
- 재시도 오류 주입: `02-retry-dlt-comparison/fault-injection/`

### Fixture와 실패 트리거 준비

01 테스트와 같은 순서로 Listener를 중단한 뒤 실행한다.

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fixture\seed-platform.sql | docker exec -i -e S08_PASSWORD_HASH=$env:S08_PASSWORD_HASH tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -v password_hash="$S08_PASSWORD_HASH" -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fixture\seed-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fixture\seed-payment-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fault-injection\setup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fault-injection\setup-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

공통 검증 SQL로 초기 상태와 중복 0건을 확인한다.

### 혼합 부하와 복구

Git Bash에서 정상 승인 1,000건과 Payment 실패 이벤트 10건을 동시에 실행한다.

```bash
./scripts/kafka/s08/run-payment-retry-dlt-load.sh
```

확인 후 Ticketing Listener를 복구하며, Ticketing이 정상 Reservation Outbox를 발행하는 동안 별도 Git Bash에서 다음을 실행한다.

```bash
./scripts/kafka/s08/run-reservation-retry-dlt-load.sh
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
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fault-injection\cleanup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fault-injection\cleanup-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fixture\cleanup-payment-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fixture\cleanup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\02-retry-dlt-comparison\fixture\cleanup-platform.sql | docker exec -i tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 03. 재시도 후 최종 성공

### 목적과 선행 조건

재시도 가능한 DB 오류가 최초 두 번 발생하고 해소됐을 때 세 번째 처리에서 정상 성공하는지 검증한다. 02 테스트의 정상 처리 1,000건 Fixture가 남아 있고 두 Consumer가 Member 1, Lag 0인 상태에서 실행한다.

### 준비와 실행

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\03-retry-success\setup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\03-retry-success\setup-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Git Bash에서 단일 Payment 성공 이벤트를 발행한다.

```bash
./scripts/kafka/s08/inject-retry-success-payment-event.sh
```

### 결과 검증

```powershell
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\03-retry-success\verify-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\03-retry-success\verify-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -x -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
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
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\03-retry-success\cleanup-ticketing.sql | docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
Get-Content -Raw .\scripts\sql\s08-kafka-recovery\03-retry-success\cleanup-notification.sql | docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

03 cleanup은 일시적 실패 트리거·함수·시퀀스만 제거한다. 전체 Fixture까지 제거하려면 02의 `fixture/cleanup-*.sql`을 Payment·Notification → Ticketing → Platform 순서로 추가 실행한다.
