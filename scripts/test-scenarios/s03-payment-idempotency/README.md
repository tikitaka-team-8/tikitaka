# S03 결제 중복 승인 및 멱등성 검증

## 목적

동일한 결제에 대해 승인 요청이 반복되거나 동시에 발생했을 때
실제 결제 승인 및 후속 처리가 중복으로 수행되지 않는지 검증한다.

주요 검증 항목은 다음과 같다.

- 동일 `paymentId` 순차 재승인 시 멱등성 유지
- 동일 `paymentId` 동시 승인 요청 시 중복 승인 방지
- PaymentTransaction 중복 생성 방지
- `PAYMENT_SUCCEEDED` Outbox 중복 생성 방지
- Reservation 중복 확정 방지
- Notification 중복 생성 방지

---

## 준비

### 실행 서비스

Docker Compose를 통해 다음 서비스가 정상 실행되어 있어야 한다.

- Gateway
- Platform Service
- Ticketing Service
- Payment & Notification Service
- PostgreSQL
- Redis
- Kafka
- Prometheus
- Grafana

### 테스트 조건

- Payment Provider: `MOCK`
- 테스트 대상 Payment 상태: `READY`
- 유효한 Access Token 필요
- 예매에 연결된 SeatHold 상태가 결제 가능한 상태여야 한다.

Postman 공용 시나리오를 이용하여 결제 생성 및 READY 조회까지 진행한다.

**결제 승인 요청은 실행하지 않는다.**

k6 테스트 시작 직전 다음 값을 준비한다.

```text
PAYMENT_ID=<READY 상태 paymentId>
ACCESS_TOKEN=<현재 사용자의 Access Token>
```

JWT 등의 인증 정보는 Git에 커밋하지 않는다.

---

## 실행

### 1. 순차 중복 승인

Postman을 이용하여 동일한 `paymentId`에 결제 승인 요청을 순차적으로 2회 수행한다.

확인 항목:

```text
최초 승인        → 200 / APPROVED
동일 결제 재승인 → 기존 승인 결과 반환
```

재승인 후 PaymentTransaction과 PaymentOutbox가 추가로 생성되지 않는지 확인한다.

### 2. 동시 승인

새로운 `READY` 상태의 Payment를 준비한다.

이 Payment는 k6 실행 전에 승인하지 않는다.

프로젝트 루트에서 다음 명령을 실행한다.

```powershell
docker run --rm `
  -v "${PWD}\scripts\test-scenarios\s03-payment-idempotency:/scripts" `
  -e BASE_URL=http://host.docker.internal:8000 `
  -e PAYMENT_ID=<READY_PAYMENT_ID> `
  -e ACCESS_TOKEN="<ACCESS_TOKEN>" `
  grafana/k6:latest run /scripts/payment-idempotency.js
```

`payment-idempotency.js`는 동일한 `paymentId`에 대해
10개의 VU가 각각 1회씩 승인 요청을 수행한다.

```text
VU: 10
VU당 요청: 1회
총 요청: 10건
```

---

## 확인

### k6

다음을 확인한다.

- 총 요청 10건
- 실제 승인 성공 건수
- 동시 요청에 대한 충돌 응답 건수
- 예상하지 못한 HTTP 응답 존재 여부
- 응답 시간

HTTP `409`는 동시 승인 과정에서 발생할 수 있는 의도된 충돌 응답이므로
k6의 `http_req_failed` 값만으로 PASS/FAIL을 판단하지 않는다.

### Payment DB

`verify-payment-consistency.sql`을 이용하여 확인한다.

주요 확인 항목:

```text
Payment.status
PaymentTransaction 건수
PaymentTransaction.status
PaymentOutbox 건수
PaymentOutbox.status
```

정상적인 동시 승인 테스트에서는 실제 APPROVE Transaction과
`PAYMENT_SUCCEEDED` Outbox가 각각 1건만 존재해야 한다.

### Reservation

테스트 대상 `reservationId`의 최종 상태를 확인한다.

```text
Reservation = CONFIRMED
```

### Notification

테스트 대상 `reservationId`에 대한 알림을 확인한다.

```text
notificationType = RESERVATION_CONFIRMED
Notification = 1건
```

### Grafana

테스트 실행 시간대를 기준으로 공용 Grafana 대시보드에서
HTTP 요청 및 애플리케이션 상태를 확인한다.

공용 Grafana 설정이나 대시보드는 시나리오 테스트를 위해 직접 수정하지 않는다.

---

## PASS 기준

다음 조건을 모두 만족하면 PASS로 판단한다.

```text
동일 paymentId 동시 요청       10건
실제 결제 승인 처리             1회
PaymentTransaction             1건
PAYMENT_SUCCEEDED Outbox       1건
Reservation                    CONFIRMED
RESERVATION_CONFIRMED 알림      1건
예상하지 못한 오류              0건
중복 후속 처리                  0건
```

동시 요청 중 실제 승인 처리 중인 Payment에 대한 요청은
합의된 충돌 응답으로 처리될 수 있다.

---

## 정리

테스트 완료 후 다음 사항을 확인한다.

- 테스트용 JWT 및 인증 정보가 파일에 저장되지 않았는지 확인
- k6 원본 결과나 로그가 필요한 경우 `artifacts/`에 보관
- 테스트 결과는 아래 문서에 기록

```text
docs/test-results/S03-payment-idempotency/local-result.md
```

테스트 결과 문서에는 실행 날짜, Branch, Commit SHA, 테스트 데이터,
실제 요청 결과, DB 정합성 검증 결과 및 제한 사항을 기록한다.