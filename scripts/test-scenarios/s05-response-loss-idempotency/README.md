# S05 응답 유실 멱등성 로컬 테스트 가이드

## 1. 테스트 목적

Ticketing Service가 예매와 Payment 생성을 완료했지만 클라이언트가 응답을 받지 못한 상황을 재현합니다. 같은 사용자가 동일한 `Idempotency-Key`와 `seatHoldId`로 재요청했을 때 기존 결과를 반환하고 중복 데이터가 생성되지 않는지 확인합니다.

현재 가이드의 범위는 S05 1단계인 **예매 생성 응답 유실 후 동일 요청 재전송**입니다. 예매 생성 이전의 인증·대기열·좌석 선점 흐름은 검증 범위에서 제외합니다.

Gateway의 인증·라우팅·Timeout이 결과에 영향을 주지 않도록 `curl.exe`에서 Toxiproxy를 거쳐 Ticketing Service로 직접 요청합니다. Ticketing에서 Payment로 이어지는 실제 내부 호출은 그대로 수행합니다.

## 2. 테스트 구성

| 구성 요소 | 역할 |
|---|---|
| `curl.exe` | Ticketing 직접 진입, 최초 요청의 전송 완료와 Client Timeout 경과 시간 확인, 동일 요청 재전송 |
| Toxiproxy | 요청은 Ticketing에 전달하고 Ticketing의 응답만 10초 지연 |
| SQL Seed | 공연·회차·ScheduleSeat·SeatHold를 예매 생성 직전 상태로 준비 |
| SQL Verify | Reservation·ReservationSeat·Payment·Outbox 중복과 연결 상태 확인 |
| 서비스 로그 | 최초 요청이 Ticketing과 Payment에서 처리됐는지 확인 |

요청 경로는 다음과 같습니다.

```text
curl.exe → Toxiproxy:18082 → Ticketing:8082 → Payment
```

Toxiproxy의 `downstream latency`는 Ticketing에서 `curl.exe`로 돌아오는 응답에만 적용합니다. curl Timeout보다 긴 지연을 적용하여 클라이언트는 실패로 인식하지만 서버 처리는 완료되는 조건을 만듭니다.

## 3. 고정 테스트 식별자

| 항목 | 값 |
|---|---|
| 요청 사용자 ID | `9500001` |
| 요청 사용자 역할 | `USER` |
| Event | `50050000-0000-0000-0000-000000000005` |
| Event Session | `50050000-0000-0000-0000-000000000006` |
| Schedule Seat | `51050000-0000-0000-0000-000000000001` |
| Seat Hold | `52050000-0000-0000-0000-000000000001` |
| Reservation Idempotency-Key | `s05-reservation-response-loss-01` |
| 금액 | `150000` |

Reservation ID와 Payment ID는 애플리케이션이 생성하므로 실행마다 달라질 수 있습니다.

## 4. 사전 준비

### 4.1 실행 버전과 시작 시각 기록

```powershell
git rev-parse HEAD
git status --short
$testStart = Get-Date -Format o
$testStart
```

실행 대상 스크립트와 문서가 Commit SHA로 추적될 수 있도록 테스트 전 변경 사항을 커밋합니다.

### 4.2 Toxiproxy 재생성

프록시 포트가 `18082`로 변경되었으므로 기존 Toxiproxy 컨테이너를 재생성합니다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --force-recreate toxiproxy

docker compose -f docker-compose.yml -f docker-compose.test.yml ps `
    ticketing-service payment-notification-service toxiproxy
```

Ticketing과 Payment가 실행 중이 아니면 다음 명령으로 시작합니다.

```powershell
docker compose up -d --wait --wait-timeout 120 ticketing-service payment-notification-service
```

### 4.3 이전 실행 데이터 정리

재실행이면 Payment → Ticketing → Platform 순서로 정리합니다. 최초 실행이면 생략합니다.

```powershell
Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\cleanup\cleanup-payment-notification.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\cleanup\cleanup-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\cleanup\cleanup-platform.sql |
    docker exec -i tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 5. Fixture 준비

### 5.1 Platform Fixture

Ticketing이 내부 API로 조회할 공연과 회차 정보를 준비합니다.

```powershell
Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\seed\seed-platform.sql |
    docker exec -i tikitaka-platform-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

### 5.2 Ticketing Fixture

고정 사용자 `9500001`이 소유한 ScheduleSeat와 SeatHold를 준비합니다.

```powershell
Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\seed\seed-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 6. 최초 요청 전 상태 확인

```powershell
Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-payment.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

최초 요청 전 기대값은 다음과 같습니다.

- `reservation_total = 0`
- `reservation_seat_total = 0`
- `payment_total = 0`
- `seat_hold_total = 1`
- `owner_user_id = 9500001`
- `hold_status = HOLDING`


## 7. Ticketing 프록시 구성

기존 S05 프록시가 있으면 제거한 뒤 Ticketing 프록시를 생성합니다.

```powershell
$toxiproxyApi = "http://localhost:8474"
$proxyName = "s05-ticketing"
$toxiproxyHeaders = @{ "User-Agent" = "s05-local-test" }

try {
    Invoke-RestMethod -Method Delete -Uri "$toxiproxyApi/proxies/$proxyName" -Headers $toxiproxyHeaders -ErrorAction Stop
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}

$proxyBody = @{
    name = $proxyName
    listen = "0.0.0.0:18082"
    upstream = "ticketing-service:8082"
    enabled = $true
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "$toxiproxyApi/proxies" `
    -Headers $toxiproxyHeaders `
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
    -Headers $toxiproxyHeaders `
    -ContentType "application/json" `
    -Body $toxicBody

Invoke-RestMethod -Method Get -Uri "$toxiproxyApi/proxies/$proxyName" -Headers $toxiproxyHeaders
```

조회 결과에서 다음 값을 확인합니다.

- `listen = 0.0.0.0:18082`
- `upstream = ticketing-service:8082`
- `enabled = True`
- `s05-response-latency`의 `stream = downstream`

## 8. 최초 예매 생성 요청과 응답 유실

요청 직전에 시각을 기록합니다.

```powershell
$firstRequestStart = Get-Date -Format o
$firstRequestStart
```

`curl.exe`로 요청을 한 번만 실행합니다.

```powershell
curl.exe --verbose --max-time 8 `
    -X POST "http://localhost:18082/api/v1/reservations" `
    -H "Content-Type: application/json" `
    -H "X-User-Id: 9500001" `
    -H "X-User-Role: USER" `
    -H "Idempotency-Key: s05-reservation-response-loss-01" `
    --data-raw '{\"seatHoldIds\":[\"52050000-0000-0000-0000-000000000001\"]}'
```

`upload completely sent off: 56 bytes`로 요청 본문 전송 완료를 확인합니다. 기대 관찰은 `8초 전후 Client Timeout`이며, HTTP 오류 응답을 기대하는 테스트가 아닙니다.

## 9. 재요청 전 최초 서버 처리 확인

curl Timeout 후 바로 재요청하지 않습니다. 먼저 동일한 검증 SQL을 실행합니다.

```powershell
Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-ticketing.sql |
    docker exec -i tikitaka-ticketing-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

Get-Content -Raw -Encoding UTF8 .\scripts\test-scenarios\s05-response-loss-idempotency\verify\verify-payment.sql |
    docker exec -i tikitaka-payment-postgres sh -c 'psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

docker compose logs --since $firstRequestStart --timestamps ticketing-service payment-notification-service
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
    -Uri "$toxiproxyApi/proxies/$proxyName/toxics/s05-response-latency" `
    -Headers $toxiproxyHeaders
```

8절과 완전히 같은 URL·헤더·본문으로 `--max-time`만 제거하여 한 번 재요청합니다.

```powershell
curl.exe --silent --show-error --include `
    -X POST "http://localhost:18082/api/v1/reservations" `
    -H "Content-Type: application/json" `
    -H "X-User-Id: 9500001" `
    -H "X-User-Role: USER" `
    -H "Idempotency-Key: s05-reservation-response-loss-01" `
    --data-raw '{\"seatHoldIds\":[\"52050000-0000-0000-0000-000000000001\"]}'
```

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

```powershell
$testEnd = Get-Date -Format o
$testEnd
```

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

결과 기록 완료를 확인한 뒤 프록시와 Fixture를 정리합니다.

```powershell
try {
    Invoke-RestMethod -Method Delete -Uri "$toxiproxyApi/proxies/$proxyName" -Headers $toxiproxyHeaders -ErrorAction Stop
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}
```

이후 4.3의 Cleanup을 Payment → Ticketing → Platform 순서로 실행합니다.

## 13. 결과 기록 항목

- 테스트 시작·종료 시각
- Branch와 Commit SHA
- curl 최초 요청의 전송 완료, Timeout 메시지와 경과 시간
- 최초 요청 후 두 검증 SQL 결과
- 동일 요청 재전송 HTTP 상태와 응답 본문
- 재요청 후 두 검증 SQL 결과
- Ticketing·Payment 로그
- Docker Compose와 Toxiproxy 버전
- 발견한 중복·고아 데이터 또는 상태 모순
