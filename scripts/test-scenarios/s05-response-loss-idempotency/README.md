# S05 응답 유실 멱등성 로컬 테스트 가이드

## 1. 테스트 목적

서버가 예매와 Payment 생성을 완료했지만 클라이언트가 응답을 받지 못한 상황을 재현합니다. 같은 사용자가 동일한 `Idempotency-Key`와 `seatHoldId`로 재요청했을 때 기존 결과를 반환하고 중복 데이터가 생성되지 않는지 확인합니다.

현재 가이드의 범위는 S05 1단계인 **예매 생성 응답 유실 후 동일 요청 재전송**입니다. 예매 생성 이전의 대기열과 좌석 선점 흐름은 SQL Fixture로 대체하고, Gateway 인증은 Postman 회원가입·로그인으로 발급한 실제 JWT를 사용합니다.

## 2. 테스트 구성

| 구성 요소 | 역할 |
|---|---|
| Postman | 회원가입·로그인, 최초 예매 요청과 동일 요청 재전송 |
| Toxiproxy | 요청은 Gateway에 전달하고 응답만 10초 지연 |
| SQL Seed | 공연·회차·ScheduleSeat·SeatHold를 예매 생성 직전 상태로 준비 |
| SQL Verify | Reservation·ReservationSeat·Payment·Outbox 중복과 연결 상태 확인 |
| 서비스 로그 | 최초 요청이 서버에 도달하고 처리됐는지 확인 |

요청 경로는 다음과 같습니다.

```text
Postman → Toxiproxy:18000 → Gateway:8000 → Ticketing → Payment
```

Toxiproxy의 `downstream latency`는 Gateway에서 Postman으로 돌아오는 응답에만 적용합니다. Postman Timeout보다 긴 지연을 적용하여 클라이언트는 실패로 인식하지만 서버 처리는 완료되는 조건을 만듭니다.

## 3. 고정 테스트 식별자

| 항목 | 값 |
|---|---|
| Event | `50050000-0000-0000-0000-000000000005` |
| Event Session | `50050000-0000-0000-0000-000000000006` |
| Schedule Seat | `51050000-0000-0000-0000-000000000001` |
| Seat Hold | `52050000-0000-0000-0000-000000000001` |
| Reservation Idempotency-Key | `s05-reservation-response-loss-01` |
| 금액 | `150000` |

Reservation ID와 Payment ID는 애플리케이션이 생성하므로 실행마다 달라질 수 있습니다.

## 4. 사전 준비

### 4.1 실행 버전 기록

```powershell
git rev-parse HEAD
git status --short
$testStart = Get-Date -Format o
$testStart
```

### 4.2 로컬 서비스와 Toxiproxy 실행

기존 서비스가 실행 중이면 Toxiproxy만 추가로 실행합니다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d toxiproxy
docker compose -f docker-compose.yml -f docker-compose.test.yml ps gateway ticketing-service payment-notification-service toxiproxy
```

전체 환경을 처음 실행하는 경우 다음 명령을 사용합니다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --wait --wait-timeout 120
```

### 4.3 이전 실행 데이터 정리

재실행이면 이전 실행에서 기록한 사용자 ID와 이메일을 설정하고 Payment → Ticketing → Platform 순서로 정리합니다. 최초 실행이면 이 절차를 생략합니다.

```powershell
$env:S05_USER_ID = "<이전 실행의 회원가입 응답 userId>"
$env:S05_USER_EMAIL = "<이전 실행에 사용한 이메일>"

Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\cleanup\cleanup-payment-notification.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\cleanup\cleanup-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\cleanup\cleanup-platform.sql |
    docker exec -i -e S05_USER_ID=$env:S05_USER_ID -e S05_USER_EMAIL=$env:S05_USER_EMAIL tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -v test_user_id="$S05_USER_ID" -v test_user_email="$S05_USER_EMAIL" -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

### 4.4 Postman 회원가입·로그인

기존 S01 Postman Collection의 회원가입과 로그인 요청을 사용합니다.

- 이메일: `s05-<실행식별자>@test.tikitaka.local`
- 역할: `USER`
- 회원가입 응답의 `userId` 기록
- 로그인 응답의 Access Token을 Postman 환경에 저장

PowerShell에도 같은 사용자 정보를 기록합니다.

```powershell
$env:S05_USER_ID = "<회원가입 응답 userId>"
$env:S05_USER_EMAIL = "<회원가입에 사용한 이메일>"
```

실제 비밀번호, JWT와 Service Key는 공유 파일이나 결과 문서에 기록하지 않습니다.

## 5. Fixture 준비

### 5.1 Platform Fixture

```powershell
Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\seed\seed-platform.sql |
    docker exec -i tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

### 5.2 Ticketing Fixture

회원가입 응답의 사용자 ID를 SeatHold 소유자로 주입합니다.

```powershell
Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\seed\seed-ticketing.sql |
    docker exec -i -e S05_USER_ID=$env:S05_USER_ID tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -v user_id="$S05_USER_ID" -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 6. 최초 요청 전 상태 확인

```powershell
Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-payment.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

최초 요청 전 기대값은 다음과 같습니다.

- `reservation_total = 0`
- `reservation_seat_total = 0`
- `payment_total = 0`
- `seat_hold_total = 1`
- `hold_status = HOLDING`

## 7. Toxiproxy 구성

기존 프록시가 있으면 제거한 뒤 Gateway 프록시를 다시 생성합니다.

```powershell
$toxiproxyApi = "http://localhost:8474"
$proxyName = "s05-gateway"

try {
    Invoke-RestMethod -Method Delete -Uri "$toxiproxyApi/proxies/$proxyName" -ErrorAction Stop
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}

$proxyBody = @{
    name = $proxyName
    listen = "0.0.0.0:18000"
    upstream = "gateway:8000"
    enabled = $true
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "$toxiproxyApi/proxies" `
    -ContentType "application/json" `
    -Body $proxyBody
```

응답 방향에 10초 지연을 추가합니다.

```powershell
$toxicBody = @{
    name = "s05-response-latency"
    type = "latency"
    stream = "downstream"
    toxicity = 1.0
    attributes = @{
        latency = 10000
        jitter = 0
    }
} | ConvertTo-Json -Depth 3

Invoke-RestMethod -Method Post `
    -Uri "$toxiproxyApi/proxies/$proxyName/toxics" `
    -ContentType "application/json" `
    -Body $toxicBody

Invoke-RestMethod -Method Get -Uri "$toxiproxyApi/proxies/$proxyName"
```

## 8. 최초 예매 생성 요청과 응답 유실

Postman의 기존 `11 예매 생성` 요청을 복제하여 다음과 같이 설정합니다.

- URL: `http://localhost:18000/api/v1/reservations`
- Authorization: 로그인에서 발급한 Bearer Token
- `Content-Type: application/json`
- `Idempotency-Key: s05-reservation-response-loss-01`
- Postman Request Timeout: `3000 ms`

요청 본문:

```json
{
  "seatHoldIds": [
    "52050000-0000-0000-0000-000000000001"
  ]
}
```

요청 직전에 시각을 기록합니다.

```powershell
$firstRequestStart = Get-Date -Format o
$firstRequestStart
```

Postman에서 요청을 한 번만 실행합니다. 기대 관찰은 `3초 전후 Client Timeout`이며, HTTP 오류 응답을 기대하는 테스트가 아닙니다.

## 9. 재요청 전 최초 서버 처리 확인

Postman Timeout 후 바로 재요청하지 않습니다. 먼저 동일한 검증 SQL을 실행합니다.

```powershell
Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-payment.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

docker compose logs --since $firstRequestStart --timestamps gateway ticketing-service payment-notification-service
```

재요청 전 기대값은 다음과 같습니다.

- Reservation 1건, `PAYMENT_PROCESSING`
- ReservationSeat 1건
- SeatHold 1건, `RESERVED`
- Payment 1건, `READY`
- Ticketing Reservation의 `payment_id`와 Payment의 `payment_id` 일치
- Ticketing Reservation의 `reservation_id`와 Payment의 `reservation_id` 일치
- 승인 전이므로 PaymentTransaction·Payment Outbox·Reservation Outbox는 각각 0건

이 상태가 확인되지 않으면 동일 요청을 재전송하지 않고 로그와 SQL 결과를 먼저 보존합니다.

## 10. 응답 지연 해제와 동일 요청 재전송

```powershell
Invoke-RestMethod -Method Delete `
    -Uri "$toxiproxyApi/proxies/$proxyName/toxics/s05-response-latency"
```

Postman Request Timeout을 원래 값으로 복원한 뒤, 8절과 완전히 같은 URL·헤더·본문으로 한 번 재요청합니다.

기대 결과:

- HTTP `200 OK`
- 기존 Reservation ID 반환
- 기존 Payment ID 반환
- `reservationStatus = PAYMENT_PROCESSING`
- `seatCount = 1`
- `totalAmount = 150000`

재요청 응답의 Reservation ID와 Payment ID를 기록합니다.

## 11. 재요청 후 최종 검증

9절과 동일한 검증 SQL을 다시 실행합니다.

최종 PASS 기준:

| 항목 | 기대값 |
|---|---:|
| Reservation | 1건 |
| ReservationSeat | 1건 |
| Payment | 1건 |
| 고유 Reservation 연결 | 1건 |
| 고유 Payment 연결 | 1건 |
| SeatHold | 1건, `RESERVED` |
| PaymentTransaction | 0건 |
| Payment Outbox | 0건 |
| Reservation Outbox | 0건 |
| 재요청 HTTP 상태 | `200 OK` |
| 재요청 반환 ID | 최초 서버 처리 결과와 동일 |

## 12. 테스트 종료와 정리

```powershell
$testEnd = Get-Date -Format o
$testEnd

try {
    Invoke-RestMethod -Method Delete -Uri "$toxiproxyApi/proxies/$proxyName" -ErrorAction Stop
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}
```

증거 자료를 기록한 뒤 4.3의 Cleanup을 Payment → Ticketing → Platform 순서로 실행합니다.

## 13. 결과 공유 항목

다음 내용을 결과 기록에 사용합니다.

- 테스트 시작·종료 시각
- Branch와 Commit SHA
- Postman 최초 요청의 Timeout 메시지와 경과 시간
- 최초 요청 후 두 검증 SQL 결과
- 동일 요청 재전송 HTTP 상태와 응답 본문
- 재요청 후 두 검증 SQL 결과
- Gateway·Ticketing·Payment 로그
- Docker Compose와 Toxiproxy 버전
- 발견한 중복·고아 데이터 또는 상태 모순
