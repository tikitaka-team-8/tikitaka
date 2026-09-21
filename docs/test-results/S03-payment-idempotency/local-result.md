# S03 결제 중복 승인 및 멱등성 검증 - Local

## 1. 테스트 개요

- 시나리오: S03 결제 승인 요청을 반복하는 사용자
- 테스트 환경: Local
- 대상 도메인: Payment
- 연계 도메인: Reservation, Notification
- 테스트 유형: Idempotency / Concurrency
- 사용 도구: Postman, k6, PostgreSQL
- Payment Provider: MOCK

- 실행일: 2026-09-16
- 실행 환경: Local / Docker Compose
- Branch: `test/111-s03-payment-idempotency`
- Commit SHA: `336f8b4d65d4d4cad2db40eded6d284b1d58a5f5`
- Payment Provider: MOCK
## 2. 테스트 목적

동일한 결제에 대해 승인 요청이 반복되거나 동시에 발생했을 때
실제 결제 승인 처리 및 후속 이벤트가 중복으로 생성되지 않는지 검증한다.

검증 항목:

- 동일 paymentId 순차 재승인
- 동일 paymentId 동시 승인 10건
- PaymentTransaction 중복 생성 여부
- Payment Outbox 중복 생성 여부
- Reservation 최종 상태
- Notification 중복 생성 여부

---

## 3. 순차 중복 승인 테스트

### 실행 방법

동일한 `paymentId`에 대해 결제 승인 API를 연속으로 2회 요청했다.

### 결과

| 항목 | 결과 |
|---|---|
| 최초 승인 | 200 / APPROVED |
| 동일 paymentId 재승인 | 200 / APPROVED |
| PaymentTransaction | 1건 |
| PaymentOutbox | 1건 |
| 중복 결제 처리 | 없음 |

### 판정

**PASS**

동일한 결제에 대한 승인 요청이 반복되어도 기존 승인 결과를 반환했으며,
PaymentTransaction과 PaymentOutbox가 추가로 생성되지 않았다.

---

## 4. 동시 승인 테스트

### 테스트 데이터

- paymentId: `17fef89c-b3e8-47ff-860a-8161dc6b8b36`
- reservationId: `99104ac9-ec75-43eb-a963-798af6e373b2`
- amount: `150000 KRW`
- 승인 전 Payment 상태: `READY`
- Payment Provider: `MOCK`

### 실행 조건

k6를 사용하여 동일한 `paymentId`에 대해 10개의 VU가 각각 1회씩
결제 승인 요청을 거의 동시에 수행했다.

```text
VU: 10
Iteration: VU당 1회
총 요청: 10건
```

| 응답      | 건수 | 내용                               |
| ------- | -: | -------------------------------- |
| 200     |  1 | 결제 승인 성공 / APPROVED              |
| 409     |  9 | P-006 / 현재 상태에서는 결제를 진행할 수 없습니다. |
| 예상 외 응답 |  0 | -                                |


k6 check 결과:
```text
checks_total      : 10
checks_succeeded  : 10
checks_failed     : 0

avg response time : 69.76ms
max response time : 72.62ms
```

※ k6의 http_req_failed는 90%로 표시되었으나, 이는 HTTP 409 응답을
기본적으로 실패 응답으로 집계하기 때문이다. 본 테스트에서는 409를
동시 승인 충돌 시 허용되는 응답으로 정의하여 별도 check로 검증했다.


## 5. DB 및 후속 처리 검증

### Payment
최종 상태:
```text
Payment.status = APPROVED
Payment.paymentProvider = MOCK
```

### PaymentTransaction:
```text
transactionType = APPROVE
provider = MOCK
status = SUCCESS
attemptNo = 1
count = 1
```
### Payment Outbox:
```text
eventType = PAYMENT_SUCCEEDED
status = PUBLISHED
retryCount = 0
count = 1
```
따라서 동시 요청 10건에 대해 실제 승인 Transaction과
PAYMENT_SUCCEEDED Outbox는 각각 1건만 생성되었다.

### Reservation
```text
reservationId = 99104ac9-ec75-43eb-a963-798af6e373b2
paymentId = 17fef89c-b3e8-47ff-860a-8161dc6b8b36
reservationStatus = CONFIRMED
```
Payment 성공 이벤트 처리 후 Reservation이 정상적으로 `CONFIRMED` 상태로 전환되었다.

### Notification

```text
notificationType = RESERVATION_CONFIRMED
readStatus = UNREAD
count = 1
```
Reservation 확정에 대한 Notification이 1건만 생성되었으며,
동시 결제 요청으로 인한 중복 알림은 발생하지 않았다.

## 6. 최종 결과

### PASS

동일한 결제에 10건의 승인 요청이 동시에 발생했을 때:

- 실제 승인 성공: 1건
- 동시 승인 충돌: 9건
- PaymentTransaction: 1건
- PAYMENT_SUCCEEDED Outbox: 1건
- Reservation: CONFIRMED
- Notification: 1건
- 중복 결제 및 중복 후속 처리: 발생하지 않음

따라서 결제 승인 동시 요청 상황에서도 결제 및 후속 처리의 정합성이 유지됨을 확인했다.
