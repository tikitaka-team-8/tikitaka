# S05 응답 유실 후 동일 요청 재전송 로컬 테스트 결과

## 1. 결과 요약

| 항목 | 내용 |
|---|---|
| 시나리오 | S05 응답 유실 후 같은 요청 재전송 |
| 담당자 | 손유진 |
| 관련 도메인 | Reservation, Seat Hold, Payment |
| 테스트 유형 | API 기능·멱등성·네트워크 장애·데이터 정합성 |
| 실행 환경 | Windows Local Docker Compose |
| 실행 회차 | Client 응답 유실 / Payment 생성 응답 유실 Baseline / 개선 후 재테스트 |
| 테스트 결과 | **1차 PASS / 2차 Baseline FAIL → 개선 후 PASS** |
| 진행 상태 | **완료** |
| 관련 Issue | [#118 S05 응답 유실 멱등성 및 Payment 생성 복구 검증](https://github.com/tikitaka-team-8/tikitaka/issues/118) |
| 관련 PR | [#125 S05 응답 유실 멱등성 검증 및 Payment 생성 복구](https://github.com/tikitaka-team-8/tikitaka/pull/125) |

Client와 Ticketing 사이의 응답만 유실된 1차 테스트에서는 기존 멱등 처리가 정상 동작했습니다. Ticketing과 Payment 사이의 응답이 유실된 2차 Baseline에서는 Ticketing만 롤백되고 Payment가 커밋되어 고아 Payment가 남았으며 동일 요청 재전송도 실패했습니다.

예매 의도를 Payment 호출 전에 커밋하도록 트랜잭션을 분리한 뒤 같은 장애 조건으로 재검증했습니다. 개선 후에는 최초 Timeout에도 Reservation과 Payment의 연결 기준이 보존됐고, 동일 요청 재전송으로 기존 예매가 중복 없이 복구됐습니다.

## 2. 진행 배경과 검증 범위

서버 처리는 완료됐지만 응답을 받지 못한 사용자가 동일한 예매 생성 요청을 재전송해도 Reservation·Payment와 연결 데이터가 중복 생성되지 않는지 확인했습니다.

검증 범위는 다음과 같습니다.

- Client가 Ticketing의 성공 응답을 받지 못한 경우의 동일 요청 재전송
- Payment 생성은 커밋됐지만 Ticketing이 Payment 응답을 받지 못한 경우의 분리 트랜잭션 상태
- 동일 사용자·`Idempotency-Key`·SeatHold 재요청의 기존 결과 반환 또는 복구
- Reservation·ReservationSeat·SeatHold·Payment·Outbox의 중복·유실·고아 데이터
- 같은 멱등 키에 다른 SeatHold를 사용한 요청의 거절

동일 `paymentId` 승인 요청의 순차·동시 중복 처리는 S03 검증 범위이므로 반복하지 않았습니다. 상세 명령과 실행 순서는 [S05 로컬 테스트 가이드](../../../scripts/test-scenarios/s05-response-loss-idempotency/README.md)에서 관리합니다.

## 3. 테스트 구성과 공통 조건

### 3.1 도구와 역할

| 구성 요소 | 역할 |
|---|---|
| curl 8.21.0 | Ticketing 직접 요청, 요청 전송 완료·Timeout·재요청 응답 확인 |
| Toxiproxy 2.12.0 | Client 응답 또는 Payment 응답의 downstream 지연 |
| SQL Seed·Verify·Cleanup | Fixture 준비, 서비스별 최종 상태 검증, 테스트 데이터 정리 |
| 서비스 로그 | Ticketing·Payment의 실제 HTTP 처리 상태와 처리시간 확인 |
| JUnit 5·Mockito | 예매 생성 규칙과 복구 조율 흐름 검증 |
| PostgreSQL Testcontainers | 선커밋과 동일 요청 복구를 실제 트랜잭션으로 검증 |

### 3.2 API 요청

| 항목 | 값 |
|---|---|
| Endpoint | `POST /api/v1/reservations` |
| 직접 진입 주소 | `http://localhost:8082/api/v1/reservations` 또는 Ticketing Toxiproxy 주소 |
| 사용자 | `X-User-Id: 9500001`, `X-User-Role: USER` |
| Idempotency-Key | `s05-reservation-response-loss-01` |
| Request Body | `seatHoldIds = [52050000-0000-0000-0000-000000000001]` |
| 회차별 요청 수 | 최초 요청 1회, 동일 요청 재전송 1회 |
| 동시 요청 수 | 1 |

### 3.3 테스트 데이터

| 항목 | 값 |
|---|---|
| Event Session | `50050000-0000-0000-0000-000000000006` |
| Schedule Seat | `51050000-0000-0000-0000-000000000001` |
| Seat Hold | `52050000-0000-0000-0000-000000000001` |
| Seat Hold 초기 상태 | `HOLDING` |
| 요청 좌석 수 | 1개 |
| 결제 금액 | 150,000원 |
| 데이터 초기화 | 각 실행 전 Cleanup 후 동일 Fixture 재생성 |

### 3.4 실행 환경

| 항목 | 값 |
|---|---|
| Branch | `test/118-s05-response-loss-idempotency` |
| 적용 Profile | `docker` |
| Docker Image Tag | `tikitaka-local-ticketing-service`, `tikitaka-local-payment-notification-service` |
| 서비스 인스턴스 수 | 서비스별 1개 |
| DB 위치 | 서비스와 동일한 로컬 PC의 Docker Compose |
| CPU | Intel Core i9-14900HX |
| 시스템 메모리 | 31.73GiB |
| Docker 할당 CPU·메모리 | 32 CPU·15.48GiB |
| PostgreSQL | 16.15 |
| Docker / Docker Compose | 29.6.1 / v5.1.4 |
| 결과 기록 Commit SHA | `853a9d7d4c8c3a0839dd75f2048c1f5262b503eb` |

### 3.5 회차별 실행 정보

| 회차 | Commit SHA | 장애 조건 |
|---|---|---|
| 1차 Client 응답 유실 | `1642c55cf98e138fcc7a17fd0a3ce40f4c5d7662` | Ticketing 응답 10,000ms 지연, curl Timeout 8,000ms |
| 2차 Payment 응답 유실 Baseline | `7f5fa03e242d86d2e53a314799acb6bfad67ccd1` | Payment 응답 5,000ms 지연, Feign Read Timeout 2,000ms |
| 개선 후 재테스트 | `9754e62aff885eb6fba897f0aa59c8b807d6450a` | 2차 Baseline과 동일 |

## 4. 1차 결과: Client → Ticketing 응답 유실

### 4.1 실행 결과

Ticketing의 응답에 10초 지연을 적용하고 curl의 최대 대기시간을 8초로 설정했습니다. 최초 요청은 본문 56 bytes를 모두 전송한 뒤 8,010ms에 Client Timeout으로 종료됐습니다.

| 관측 항목 | 실제 결과 |
|---|---|
| 최초 요청 시작 시각 | `2026-09-17T15:55:23.7108666+09:00` |
| Client 결과 | 응답 0 bytes, Timeout |
| Ticketing 처리 | `201 Created`, 854ms |
| Payment 처리 | `201 Created`, 501ms |
| 서버 처리 완료 여부 | 완료 |

재요청 전에 Reservation·ReservationSeat·Payment가 각각 1건 생성됐고 SeatHold가 `RESERVED`로 전환된 것을 확인했습니다. Reservation과 Payment의 상호 ID 연결도 일치했습니다.

### 4.2 동일 요청 재전송

| 항목 | 기대값 | 실제값 |
|---|---|---|
| HTTP 상태 | `200 OK` | `200 OK` |
| Reservation ID | 최초 처리 ID와 동일 | `5c093815-5dd5-4e11-b988-33be37f84dbe` / 동일 |
| Payment ID | 최초 처리 ID와 동일 | `e2862bda-023e-4c60-ad49-6c4562c1f78c` / 동일 |
| Reservation 상태 | `PAYMENT_PROCESSING` | `PAYMENT_PROCESSING` |
| 좌석 수·결제 금액 | 1개·150,000원 | 1개·150,000원 |

최종 Reservation·ReservationSeat·Payment는 각각 1건이었으며 PaymentTransaction·Payment Outbox·Reservation Outbox는 승인 전 단계이므로 0건이었습니다. 중복·유실·고아 데이터는 발견되지 않았습니다.

### 4.3 판정

**PASS**입니다. Client 응답 유실은 서버 처리 결과에 영향을 주지 않았으며 동일 요청 재전송이 기존 결과를 반환했습니다. 사전 Postman 시도는 요청이 Ticketing에 전달되기 전에 전체 요청 Timeout이 발생하여 결과에서 제외했고, 요청 전송 완료를 확인할 수 있는 curl 기반 절차로 변경했습니다.

## 5. 2차 Baseline: Ticketing → Payment 생성 응답 유실

### 5.1 진행 이유와 사전 가설

1차 테스트는 Ticketing 내부 처리가 이미 완료된 뒤 Client 응답만 유실된 경우였습니다. Payment가 결제를 커밋한 뒤 Ticketing이 Payment 응답을 받지 못하는 서비스 간 분리 트랜잭션 상황은 별도로 확인할 필요가 있었습니다.

코드와 트랜잭션 경계를 기준으로 다음 결과를 예상했습니다.

1. Payment에 `READY` 결제 1건이 커밋됩니다.
2. Ticketing은 Payment 응답 Timeout으로 Reservation·ReservationSeat·SeatHold 변경을 롤백합니다.
3. Payment가 Ticketing에 존재하지 않는 Reservation ID를 참조합니다.
4. 재요청에서 생성한 새 Reservation ID가 기존 Payment의 Reservation ID와 달라 복구에 실패합니다.

### 5.2 실제 결과

| 관측 항목 | 실제 결과 |
|---|---|
| 최초 요청 시작 시각 | `2026-09-17T23:00:45.9528745+09:00` |
| Ticketing 응답 | `504 C-004`, 2,199ms |
| Payment 처리 | `201 Created`, 93ms |
| Ticketing DB | Reservation·ReservationSeat 0건, SeatHold `HOLDING` |
| Payment DB | Payment `READY` 1건 |
| Payment ID | `59c574cf-9c72-4498-a691-f597dc3ef9a1` |
| Payment의 Reservation ID | `33bba543-29c7-4d14-83cb-1fd69cafd107` |
| 고아 데이터 | Ticketing에 존재하지 않는 Reservation을 참조하는 Payment 1건 |

동일 요청 재전송 결과 Payment는 `409`를 반환했고 Ticketing은 `503 R-008`을 반환했습니다. 재요청 후에도 Reservation·ReservationSeat는 0건, SeatHold는 `HOLDING`, Payment는 `READY` 1건으로 유지되어 복구되지 않았습니다. 사전 가설 1~4가 모두 실제 결과와 일치했습니다.

### 5.3 판정

**FAIL**입니다. Payment 생성은 성공했지만 Ticketing에 예매가 존재하지 않는 서비스 간 상태 불일치가 발생했고, 동일 요청 재전송도 이를 복구하지 못했습니다.

## 6. 발견한 문제와 구현 개선

### 6.1 근본 원인

Reservation 저장부터 Payment 생성 HTTP 호출까지 하나의 Ticketing 트랜잭션에서 수행했습니다. Payment가 `201 Created`로 커밋한 뒤 응답만 늦어져도 Ticketing은 Timeout 예외로 전체 트랜잭션을 롤백했습니다.

재요청에서는 기존 Reservation이 없으므로 새 Reservation ID를 생성했습니다. Payment는 멱등 키에 연결된 기존 결제를 찾았지만 기존 Reservation ID와 새 Reservation ID가 달라 중복 요청 충돌을 반환했습니다.

### 6.2 개선 내용

처리 흐름을 다음 세 구간으로 분리했습니다.

1. 준비 트랜잭션에서 Reservation을 `PAYMENT_PENDING`으로 저장하고 ReservationSeat 생성과 SeatHold `RESERVED` 전환을 커밋합니다.
2. DB 트랜잭션 밖에서 Payment 생성 API를 호출합니다.
3. 완료 트랜잭션에서 Payment 응답을 검증하고 `paymentId` 연결과 `PAYMENT_PROCESSING` 전환을 커밋합니다.

Payment 응답을 받지 못하면 준비 트랜잭션의 결과를 유지합니다. 같은 요청이 재전송되면 기존 `PAYMENT_PENDING` Reservation을 조회하고 동일한 Reservation ID·사용자·금액·멱등 키로 Payment를 재호출합니다. Payment가 기존 결제를 반환하면 완료 트랜잭션을 수행하여 예매를 복구합니다.

Payment 도메인의 API나 이벤트 계약은 변경하지 않고 기존 결제 생성 멱등 처리를 활용했습니다.

## 7. 개선 후 동일 조건 재테스트

### 7.1 동일 조건 확인

| 항목 | 확인 결과 |
|---|---|
| 실행 환경 | 동일한 로컬 Docker Compose와 서비스별 단일 인스턴스 사용 |
| 테스트 데이터 | 같은 사용자·Event Session·ScheduleSeat·SeatHold·금액 사용 |
| 요청 조건 | 같은 Endpoint·Headers·Body·Idempotency-Key 사용 |
| 장애 조건 | Payment downstream 5,000ms 지연과 Feign Read Timeout 2,000ms 유지 |
| 달라진 조건 | 개선 코드 Commit과 실행 시각만 변경 |

### 7.2 최초 Timeout 후 상태

| 관측 항목 | 실제 결과 |
|---|---|
| 최초 요청 시작 시각 | `2026-09-18T14:18:33.4537195+09:00` |
| Ticketing 응답 | `504 C-004`, 2,522ms |
| Payment 처리 | `201 Created`, 415ms |
| Reservation | 1건, `PAYMENT_PENDING` |
| ReservationSeat | 1건 |
| SeatHold | 1건, `RESERVED` |
| Payment | 1건, `READY` |
| Reservation.payment_id | `NULL` |
| Reservation ID | `f4576900-6444-4c11-9651-45f92d55875d` |
| Payment ID | `57e4ab58-50ed-41e4-b750-27f765a47906` |
| 고아 데이터 | 0건, Reservation·Payment의 Reservation ID 일치 |

### 7.3 동일 요청 재전송 결과

| 관측 항목 | 실제 결과 |
|---|---|
| 재요청 시작 시각 | `2026-09-18T14:20:55.2395103+09:00` |
| Ticketing 응답 | `200 OK`, 50ms |
| Payment 처리 | `201 Created`, 12ms |
| Reservation | 1건, `PAYMENT_PROCESSING` |
| ReservationSeat | 1건 |
| SeatHold | 1건, `RESERVED` |
| Payment | 1건, `READY` |
| 연결 상태 | 기존 Payment ID가 기존 Reservation에 연결됨 |
| 중복·유실·고아 데이터 | 0건 |

Payment가 재요청에 `201 Created`를 반환했지만 DB의 Payment는 기존 1건으로 유지됐으며 기존 Payment ID가 반환됐습니다.

### 7.4 개선 전·후 비교

| 관측 항목 | 개선 전 | 개선 후 | 변화                 |
|---|---|---|--------------------|
| 최초 Timeout 후 Reservation | 0건, 전체 롤백 | 1건, `PAYMENT_PENDING` | 복구 기준점 확보          |
| 최초 Timeout 후 Payment | Ticketing에 없는 Reservation 참조 | Ticketing Reservation과 ID 일치 | 고아 Payment 발생하지 않음 |
| 최초 Timeout 후 SeatHold | `HOLDING` | `RESERVED` | 예매 처리 권한 유지        |
| 동일 요청 재전송 | Ticketing `503`, Payment `409` | Ticketing `200`, Payment `201` | 복구 실패 → 성공         |
| Ticketing 재요청 처리시간 | 77ms | 50ms | 27ms·35.1% 감소      |
| Payment 재요청 처리시간 | 52ms | 12ms | 40ms·76.9% 감소      |
| 최종 Reservation·Payment | Reservation 0건·Payment 1건 | Reservation 1건·Payment 1건 | 연결 정합성 회복          |
| 최종 중복·고아 데이터 | 고아 Payment 1건 | 0건 | 100% 제거            |

최초 504 응답시간은 의도적으로 설정한 Feign Timeout과 프록시 지연의 결과이므로 성능 개선 지표로 사용하지 않았습니다. Ticketing·Payment 재요청 처리시간도 각 회차의 단일 실행에서 얻은 참고 관측값으로, 성능 개선 지표가 아닙니다. 개선 여부는 재요청 복구 성공과 최종 데이터 정합성을 기준으로 판정했습니다.

## 8. 자동 테스트와 최종 판정

### 8.1 자동 테스트

| 검증 | 결과 |
|---|---|
| Reservation 기존 규칙과 신규 복구 경로 단위 테스트 | 32개 PASS |
| Payment 호출 실패 후 `PAYMENT_PENDING` 유지 | PASS |
| 기존 `PAYMENT_PENDING` 동일 요청의 결제 연결 복구 | PASS |
| PostgreSQL 기반 선커밋·재요청 복구 통합 테스트 | Testcontainers 실행 PASS |
| 같은 멱등 키에 다른 SeatHold 사용 | `IDEMPOTENCY_KEY_REUSED` 거절 PASS |

### 8.2 최종 데이터 정합성

| 항목 | 최종 결과 |
|---|---|
| Reservation | 1건, `PAYMENT_PROCESSING` |
| ReservationSeat | 1건 |
| SeatHold | 1건, `RESERVED` |
| Payment | 1건, `READY` |
| PaymentTransaction | 0건 |
| Payment Outbox | 0건 |
| Reservation Outbox | 0건 |
| 중복 데이터 | 0건 |
| 유실 데이터 | 0건 |
| 고아 데이터 | 0건 |
| 서비스 간 상태 불일치 | 없음 |

### 8.3 최종 판정

- 테스트 결과: **PASS**
- 진행 상태: **완료**
- 판정 근거: 동일 장애 조건에서 최초 Timeout 후 복구 기준점이 보존됐고, 동일 요청 재전송이 기존 Reservation·Payment를 중복 없이 연결했습니다.

## 9. 증거 자료와 추적 정보

| 구분 | 위치 |
|---|---|
| 상세 실행 가이드 | `scripts/test-scenarios/s05-response-loss-idempotency/README.md` |
| Seed SQL | `scripts/test-scenarios/s05-response-loss-idempotency/seed/` |
| Verify SQL | `scripts/test-scenarios/s05-response-loss-idempotency/verify/` |
| Cleanup SQL | `scripts/test-scenarios/s05-response-loss-idempotency/cleanup/` |
| 단위 테스트 | `ticketing-service/src/test/java/com/tikitaka/ticketing/reservation/application/ReservationServiceTest.java` |
| 통합 테스트 | `ticketing-service/src/test/java/com/tikitaka/ticketing/reservation/application/ReservationResponseLossRecoveryIntegrationTest.java` |
| 관련 Issue | [#118 [Test] S05 응답 유실 멱등성 및 Payment 생성 복구 검증](https://github.com/tikitaka-team-8/tikitaka/issues/118) |
| 관련 PR | [#125 [Test/Fix] S05 응답 유실 멱등성 검증 및 Payment 생성 복구](https://github.com/tikitaka-team-8/tikitaka/pull/125) |

테스트 종료 후 Payment 프록시 삭제를 확인했고 Ticketing의 Payment URL과 Feign Connect·Read Timeout을 기본값인 직접 서비스 주소, 1,000ms·60,000ms로 복원했습니다. S05 Fixture는 Payment → Ticketing → Platform 순서로 정리했습니다.
