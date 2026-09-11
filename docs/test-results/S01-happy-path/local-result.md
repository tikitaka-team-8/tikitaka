# S01 로컬 Happy Path 테스트 결과

## 기본 정보

- 시나리오: 정상적으로 예매하는 사용자 민지
- 담당: Infra·Gateway
- 관련 도메인: 전체 도메인
- 테스트 유형: E2E·Smoke, API 기능, 데이터·이벤트 정합성 확인
- 실행 회차: 1차 수동 검증
- 실행 일시: 2026-09-10
- 실행 환경: Local Docker Compose
- Branch: `test/98-local-integration-test`
- 테스트 대상 Commit SHA: `4ac1485` (`ci: Notification 최종 상태 검증과 실패 진단 보완`)
- 문서 작성 시점 HEAD: `74ce762d406c0863c4e0cc16611a75d717e7f3ce`
- Docker Image Tag: 로컬 Compose 빌드 이미지
- 적용 Profile: `docker`
- 테스트 사용자: 실행 시 생성한 일반 `USER` 1명(이메일·비밀번호 미기록)
- 테스트 대상: 공개 공연 1개, 회차 1개, 좌석 1개
- 네트워크 및 요청 조건: 정상 네트워크, 의도적인 중복·동시 요청 없음
- 사용 도구: Postman, PostgreSQL DB Console, Kafbat UI, Prometheus
- Postman Collection: [`scripts/postman/tikitaka.postman_collection.json`](../../../scripts/postman/tikitaka.postman_collection.json)
- Postman Environment: [`scripts/postman/tikitaka.local.example.postman_environment.json`](../../../scripts/postman/tikitaka.local.example.postman_environment.json)
- 관련 Issue: #98


## 테스트 목적

Gateway를 단일 진입점으로 사용하여 회원가입부터 결제 승인, Kafka 기반 예매 확정, 알림 생성과 읽음 처리까지 연결되는지 확인한다. 최종적으로 Payment, Reservation, Seat Hold, Seat, Outbox, Inbox와 Notification 상태가 하나의 정상 예매 결과로 일치하는지 검증한다.


## 테스트 데이터와 선행 조건

### 고정 Fixture

| 항목 | 값 |
|---|---|
| Event ID | `30000000-0000-0000-0000-000000000001` |
| Session ID | `31000000-0000-0000-0000-000000000001` |
| 기대 좌석 가격 | `150000` |
| 테스트 좌석 | 위 회차에서 조회된 `AVAILABLE` VIP 좌석 1개 |

### 데이터 준비

1. Docker Compose로 애플리케이션과 PostgreSQL, Redis, Kafka 및 모니터링 구성을 실행한다.
2. Platform DB에 [`platform-seed.sql`](../../../scripts/integration-test/seed/platform-seed.sql)을 적용한다.
3. Ticketing DB에 [`ticketing-seed.sql`](../../../scripts/integration-test/seed/ticketing-seed.sql)을 적용한다.
4. Postman에서 로컬 Environment를 선택하고 Collection 요청을 번호 순서대로 실행한다.

- 기존 데이터 초기화: 볼륨 전체 초기화 수행
- Fixture 처리: Seed를 테스트 시작 전에 적용
- 실행별 데이터: 회원가입 정보와 `Idempotency-Key`는 실행별 고유값 사용
- 주의: Ticketing Seed는 좌석을 `AVAILABLE`로 되돌리므로 시나리오 실행 중 재적용하지 않는다.

## 실행 순서와 결과

| 순서 | 행동 | Method 및 Endpoint | 기대 결과 | 실제 결과 |
|---:|---|---|---|---|
| 1 | 회원가입 | `POST /api/v1/auth/signup` | `201`, USER 생성 | 최초 인증 설정 오류 수정 후 성공 |
| 2 | 로그인 | `POST /api/v1/auth/login` | `200`, Access Token 발급 | 성공 |
| 3 | 공연 목록 | `GET /api/v1/events` | Fixture 공연 포함 | 성공 |
| 4 | 공연 상세 | `GET /api/v1/events/{eventId}` | Event ID 일치 | 성공 |
| 5 | 회차·가격 | `GET /api/v1/events/{eventId}/sessions/{sessionId}` | Session ID, 150,000원 가격 확인 | 성공 |
| 6 | 대기열 진입 | `POST /api/v1/event-sessions/{sessionId}/queue` | 인증 사용자 Entry 생성 | 성공 |
| 7 | 입장 확인 | `GET /api/v1/event-sessions/{sessionId}/queue/me` | `ADMITTED`, admissionToken 발급 | 성공 |
| 8 | 좌석 목록 | `GET /api/v1/schedules/{sessionId}/seats` | `AVAILABLE` 좌석 조회 | 성공 |
| 9 | 좌석 상세 | `GET /api/v1/schedules/{sessionId}/seats/{scheduleSeatId}` | ID·가격·상태 일치 | 성공 |
| 10 | 좌석 선점 | `POST /api/v1/schedules/{sessionId}/seats/{scheduleSeatId}/hold` | Seat Hold 및 만료시각 생성 | 성공 |
| 11 | 예매 생성 | `POST /api/v1/reservations` | `201`, `PAYMENT_PROCESSING`, Payment 생성 | 성공 |
| 12 | 결제 조회 | `GET /api/v1/payments/{paymentId}` | `READY`, 금액·예매 ID 일치 | 성공 |
| 13 | 결제 승인 | `POST /api/v1/payments/{paymentId}/approve` | `APPROVED` | 성공 |
| 14 | 예매 확정 | `GET /api/v1/reservations/{reservationId}` | 비동기 처리 후 `CONFIRMED` | 성공 |
| 15 | 알림 식별 | `GET /api/v1/notifications?notificationType=RESERVATION_CONFIRMED&readStatus=UNREAD&size=50` | 현재 예매번호의 미확인 알림 1건 식별 | 성공 |
| 16 | 알림 상세·읽음 | `PATCH /api/v1/notifications/{notificationId}/read` | 예매 연결 필드 일치, `READ`, `lastViewedAt` 설정 | 성공 |

### 필수 Header와 입력값

- 회원가입·로그인: `No Auth`
- 로그인 이후 보호 API: `Authorization: Bearer {accessToken}`
- 좌석 목록·상세·선점: `X-Queue-Token: {admissionToken}`
- 좌석 선점 및 예매 생성: 실행별 고유 `Idempotency-Key`
- 예매 생성 Body: 현재 사용자의 `seatHoldId` 1개
- 결제 승인 Body: `paymentMethod=CARD`
- 외부 요청에서 `X-User-Id`, `X-User-Role`, `X-Service-Key`를 신뢰값으로 직접 설정하지 않음


## 데이터 및 이벤트 확인 결과

### Ticketing DB

| 대상 | 기대 상태 | 실제 확인 |
|---|---|---|
| Reservation | `CONFIRMED` | 일치 |
| Seat Hold | `CONFIRMED` | 일치 |
| Schedule Seat | `SOLD` | 일치 |
| Reservation Outbox | `RESERVATION_CONFIRMED`, `PUBLISHED`, 1건 | 일치 |

검증 스크립트:

- [`verify-ticketing-state.sql`](../../../scripts/integration-test/verify-ticketing-state.sql)
- [`verify-ticketing-event-state.sql`](../../../scripts/integration-test/verify-ticketing-event-state.sql)

### Payment·Notification DB

| 대상 | 기대 상태 | 실제 확인 |
|---|---|---|
| Payment | `APPROVED` | 일치 |
| Payment Outbox | `PAYMENT_SUCCEEDED`, `PUBLISHED`, 1건 | 일치 |
| Notification Inbox | 현재 Reservation 이벤트 1건 | 일치 |
| Notification | `RESERVATION_CONFIRMED`, 1건 | 일치 |
| Notification 읽음 | `READ`, `lastViewedAt` 존재 | 일치 |

검증 스크립트:

- [`verify-payment-notification-state.sql`](../../../scripts/integration-test/verify-payment-notification-state.sql)

### Redis

- Queue에서 `ADMITTED` 상태와 admissionToken 발급을 API로 확인했다.
- 좌석 선점이 성공하고 이후 DB 상태가 `CONFIRMED/SOLD`로 전환된 것을 확인했다.
- Redis 키 이름, TTL과 메모리 수치는 이번 실행 결과에 별도로 보존하지 않았다.

### Kafka

- Kafbat UI: `http://localhost:8085`
- `payment-events`에서 결제 성공 후속 흐름을 확인했다.
- `reservation-events`에서 예매 확정 후 Notification 연결 흐름을 확인했다.
- 최종적으로 Reservation과 Notification Consumer 처리가 완료되고 관련 DB의 Inbox·Outbox 상태가 기대값과 일치했다.
- 이벤트별 timestamp, payload 캡처와 처리 지연 수치는 별도로 보존하지 않았다.

### Prometheus

- Prometheus: `http://localhost:9090`
- 애플리케이션 메트릭 수집 화면과 HTTP 요청 지표를 보조적으로 확인했다.
- S01은 단일 사용자의 기능 Smoke Test이므로 성능·부하 판정에는 사용하지 않았다.
- 서비스별 요청량, p95·p99, CPU·메모리의 정량값은 이번 실행에서 기록하지 않았다.
- 시나리오별 Grafana Dashboard는 아직 구성되지 않았으며 추후 관측성 작업으로 진행한다.

## 정합성 확인

| 항목 | 결과 |
|---|---|
| 중복 결제·예매·알림 | 정상 흐름에서 중복 없음 |
| 이벤트 유실 | 최종 상태 기준 유실 없음 |
| 고아 데이터 | 확인되지 않음 |
| 서비스 간 상태 불일치 | 확인되지 않음 |
| Payment → Reservation → Notification 연결 | 확인 |
| Reservation·Seat Hold·Seat 최종 상태 | `CONFIRMED / CONFIRMED / SOLD` |
| Payment·Notification 최종 상태 | `APPROVED / READ` |
| Timeout | 없음 |
| Lock Wait·Deadlock | 이번 단일 요청 시나리오의 측정 대상 아님 |

이번 결과는 정상 네트워크와 단일 사용자 조건의 Happy Path에 한정한다. 중복 이벤트, 동시 승인, Seat Hold 만료 경합과 Consumer 중단·복구는 각각 S02·S03·S04·S08에서 별도로 검증한다.

## 최종 판정

- 테스트 결과: **PASS**
- 진행 상태: **완료**
- 판정 근거:
  - Gateway를 경유한 회원가입부터 결제 승인까지 API 흐름이 성공했다.
  - `payment-events` 처리 후 Reservation·Seat Hold·Seat가 각각 `CONFIRMED`, `CONFIRMED`, `SOLD`로 일치했다.
  - Reservation 결과 이벤트가 발행·소비되어 Notification과 Inbox가 생성됐다.
  - Notification을 현재 예매번호로 식별하고 `READ` 및 `lastViewedAt`까지 확인했다.
  - Payment·Reservation Outbox가 `PUBLISHED` 상태이며 정상 실행에서 중복 레코드가 생성되지 않았다.
- 완료 범위:
  - GitHub Actions Happy Path workflow 보완
  - 공용 Postman Collection·Environment 및 로컬 실행 결과 문서 추가
- 후속 확인 사항:
  - 다른 로컬 시나리오가 모두 안정화된 후 스테이징에서 S01 대표 Smoke Test 수행
  - 시나리오별 Grafana Dashboard와 추가 관측 지표는 별도 관측성 작업에서 구성