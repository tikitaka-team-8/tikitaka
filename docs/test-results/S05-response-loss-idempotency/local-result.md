# S05 응답 유실 후 동일 요청 재전송 로컬 테스트 결과

## 1. 결과 요약

| 항목 | 내용                                                                                              |
|---|-------------------------------------------------------------------------------------------------|
| 시나리오 | S05 응답 유실 후 같은 요청 재전송                                                                           |
| 담당자 | 손유진                                                                                             |
| 관련 도메인 | Reservation, Seat Hold, Payment                                                                 |
| 테스트 유형 | 멱등성·네트워크 장애·데이터 정합성                                                                             |
| 실행 환경 | Windows Local Docker Compose                                                                    |
| 실행 회차 | 1차 Client 응답 유실 / 2차 Payment 생성 응답 유실 Baseline / 개선 후 재테스트                                      |
| 테스트 결과 | **1차 PASS / 2차 Baseline FAIL → 개선 후 PASS**                                                                        |
| 진행 상태 | **완료**                                                                                       |
| 관련 Issue | [#118 S05 응답 유실 멱등성 및 Payment 생성 복구 검증](https://github.com/tikitaka-team-8/tikitaka/issues/118) |
| 관련 PR | 작성 후 연결 예정                                                                                      |

1차에서는 Client 응답이 유실돼도 서버 처리·동일 요청 재전송·최종 정합성이 모두 정상이었습니다. 2차 Baseline에서는 Payment 생성 응답이 Ticketing에 도달하지 않았을 때 Ticketing만 롤백되고 Payment가 커밋되어 고아 데이터가 남았으며, 동일 요청 재전송으로도 복구되지 않음을 확인했습니다. 예매 의도 저장과 Payment 호출, 결제 연결 완료의 트랜잭션 경계를 분리한 뒤 같은 장애 조건으로 재검증하여 중복·고아 데이터 없이 기존 예매를 복구했습니다.

## 2. 테스트 목적과 범위

서버가 예매와 Payment 생성을 완료한 뒤 클라이언트 응답만 유실된 상황에서 같은 요청을 재전송해도 Reservation·Payment와 연결 데이터가 중복 생성되지 않는지 검증합니다.

이번 1차 범위는 다음과 같습니다.

- 예매 생성 요청을 서버까지 정상 전달
- Toxiproxy로 응답 방향만 지연하여 Client Timeout 재현
- 재요청 전 최초 서버 커밋 확인
- 동일 사용자·멱등 키·SeatHold로 한 번 재요청
- 기존 Reservation·Payment 반환 및 최종 중복·고아 데이터 확인

S03에서 수행한 동일 `paymentId` 승인 요청의 순차·동시 중복 검증은 반복하지 않습니다. S05에서는 실제 응답 유실 조건과 재요청 전 서버 처리 완료 여부를 중심으로 확인합니다.

## 3. 테스트 구성과 도구 역할

| 구성 요소 | 역할 |
|---|---|
| curl 8.21.0 | Ticketing 직접 진입, 요청 본문 전송 완료와 Client Timeout 경과 시간 확인, 동일 요청 재전송 |
| Toxiproxy 2.12.0 | `curl → Ticketing` 구간의 downstream 응답 10초 지연 |
| SQL Seed | 예매 생성 직전의 공연·회차·ScheduleSeat·SeatHold 상태 준비 |
| SQL Verify | 멱등 키 기준 Reservation·Payment·Seat·Outbox 집계 |
| SQL Cleanup | 세 DB의 S05 전용 데이터만 FK 의존 순서에 맞춰 정리 |
| 서비스 로그 | 최초 요청 도달과 서버 처리 결과 추적 |

```text
curl.exe → Toxiproxy:18082 → Ticketing:8082 → Payment
```

상세 재현 순서와 명령은 [S05 로컬 테스트 가이드](../../../scripts/test-scenarios/s05-response-loss-idempotency/README.md)에 분리했습니다.

## 4. 공통 실행 정보

| 항목 | 값                                        |
|---|------------------------------------------|
| 실행 일시 | 2026-09-17 15:08:06~16:05:40 KST             |
| 담당자 | 손유진                                      |
| 실행 환경 | Local                                    |
| Branch | `test/118-s05-response-loss-idempotency` |
| 실행 대상 Commit SHA | `1642c55cf98e138fcc7a17fd0a3ce40f4c5d7662` |
| 결과 기록 Commit SHA | 결과 커밋 후 기록 예정                            |
| Docker Image Tag | `tikitaka-local-ticketing-service`, `tikitaka-local-payment-notification-service` |
| 적용 Profile | `docker`                                 |
| 서비스 인스턴스 수 | 서비스별 1개                                  |
| DB·Kafka 위치 | 서비스와 동일한 로컬 PC의 Docker Compose           |
| 테스트 데이터 | S05 전용 SQL Fixture, 실행 전 초기화             |
| 요청 사용자 | `X-User-Id: 9500001`, `X-User-Role: USER` |
| 관련 대시보드 | 해당 없음                                    |

### 4.1 실행 머신과 도구 버전

| 항목 | 값 |
|---|---|
| CPU | Intel Core i9-14900HX |
| 시스템 메모리 | 31.73GiB |
| Docker 할당 CPU·메모리 | 32 CPU·15.48GiB |
| PostgreSQL | `16.15` |
| curl | `8.21.0` |
| Docker / Docker Compose | `29.6.1` / `v5.1.4` |
| Toxiproxy | `2.12.0` |

이 테스트는 부하 성능 측정이 아니므로 VU·Ramp-up·처리량·p95·p99는 적용하지 않습니다. 대신 최초 처리 완료 여부, Client Timeout, 재요청 결과, 데이터 건수와 연결 정합성을 기록합니다.

## 5. 테스트 데이터와 장애 조건

| 항목 | 값 |
|---|---|
| Event Session | `50050000-0000-0000-0000-000000000006` |
| Schedule Seat | `51050000-0000-0000-0000-000000000001` |
| Seat Hold | `52050000-0000-0000-0000-000000000001` |
| Seat Hold 초기 상태 | `HOLDING` |
| Reservation Idempotency-Key | `s05-reservation-response-loss-01` |
| 요청 좌석 수 | 1개 |
| 결제 금액 | 150,000원 |
| Toxiproxy 방향 | `downstream` |
| 응답 지연 | 10,000ms |
| curl `--max-time` | 8,000ms |
| 재요청 횟수 | 1회 |

## 6. 1차 Baseline: Client → Ticketing 응답 유실

### 6.1 PASS 기준

1. 최초 curl 요청은 본문 전송을 완료한 뒤 Client Timeout으로 종료됩니다.
2. 재요청 전에 서버가 Reservation과 Payment 생성을 완료한 사실을 SQL과 로그로 확인할 수 있습니다.
3. 동일 요청 재전송은 `200 OK`와 기존 Reservation·Payment ID를 반환합니다.
4. Reservation·ReservationSeat·Payment는 각각 1건만 존재합니다.
5. Reservation과 Payment가 서로 같은 Reservation ID와 Payment ID로 연결됩니다.
6. SeatHold는 1건이며 `RESERVED` 상태입니다.
7. 승인 전 단계이므로 PaymentTransaction·Payment Outbox·Reservation Outbox는 0건입니다.
8. 중복·유실·고아 데이터와 상태 모순이 없습니다.

### 6.2 실행 결과

#### 최초 요청 관찰

| 항목 | 실제 결과 |
|---|---|
| 최초 요청 시작 시각 | `2026-09-17T15:55:23.7108666+09:00` |
| curl 관찰 결과 | 56 bytes 전송 완료 후 응답 0 bytes인 상태로 Timeout |
| Client Timeout 경과 시간 | 8,010ms |
| Ticketing 처리 | `201 Created`, 854ms |
| Payment 내부 처리 | `201 Created`, 501ms |
| 서버 최초 처리 완료 여부 | 완료 |

#### 재요청 전 DB 상태

| 항목 | 기대값 | 실제값 |
|---|---:|---:|
| Reservation | 1 | 1 |
| ReservationSeat | 1 | 1 |
| Payment | 1 | 1 |
| SeatHold `RESERVED` | 1 | 1 |
| PaymentTransaction | 0 | 0 |
| Payment Outbox | 0 | 0 |
| Reservation Outbox | 0 | 0 |

- Reservation ID: `5c093815-5dd5-4e11-b988-33be37f84dbe`
- Payment ID: `e2862bda-023e-4c60-ad49-6c4562c1f78c`
- Reservation Number: `RSV-260917-31EF5ECD5FDC`
- Reservation·Payment의 상호 ID 연결이 일치함

#### 동일 요청 재전송 결과

| 항목 | 기대값 | 실제값 |
|---|---|---|
| 재요청 시작 시각 | - | `2026-09-17T16:03:09.5622289+09:00` |
| HTTP 상태 | `200 OK` | `200 OK` |
| Reservation ID | 최초 처리 ID와 동일 | `5c093815-5dd5-4e11-b988-33be37f84dbe` / 동일 |
| Payment ID | 최초 처리 ID와 동일 | `e2862bda-023e-4c60-ad49-6c4562c1f78c` / 동일 |
| Reservation Number | 최초 처리 값과 동일 | `RSV-260917-31EF5ECD5FDC` / 동일 |
| Reservation 상태 | `PAYMENT_PROCESSING` | `PAYMENT_PROCESSING` |
| 좌석 수·결제 금액 | 1개·150,000원 | 1개·150,000원 |

#### 재요청 후 최종 DB 상태

| 항목 | 기대값 | 실제값 |
|---|---:|---:|
| Reservation | 1 | 1 |
| 고유 Payment 연결 | 1 | 1 |
| ReservationSeat | 1 | 1 |
| Payment | 1 | 1 |
| 고유 Reservation 연결 | 1 | 1 |
| 중복·고아 데이터 | 0 | 0 |

- SeatHold: 1건, `RESERVED`
- Payment: 1건, `READY`, 150,000원
- PaymentTransaction·Payment Outbox·Reservation Outbox: 각 0건
- 재요청 Ticketing 로그: `200 OK`, 18ms

### 6.3 발견 문제와 원인

사전 준비 과정에서 Postman의 전체 요청 Timeout이 Toxiproxy 연결 후 Ticketing으로 요청이 전달되기 전에 발생했습니다. 해당 시도는 DB 변경과 Ticketing 로그가 없어 Baseline에서 제외했습니다. 요청 본문 전송 완료를 명시적으로 관찰할 수 있는 `curl.exe --verbose --max-time 8`로 변경하여 유효한 Baseline을 확보했습니다.

운영 코드의 문제는 발견되지 않았습니다. 최초 서버 처리, 동일 요청 재전송, 최종 데이터 정합성이 모두 기대값과 일치했습니다.

### 6.4 개선 내용과 동일 조건 재테스트

이 회차에서는 운영 코드 문제가 발견되지 않아 구현 변경과 개선 전·후 재테스트를 수행하지 않았습니다. Postman을 사용한 사전 시도는 테스트 도구 설정 문제로 분리했으며, 실제 전송 완료를 확인할 수 있는 curl 기반 절차로 가이드를 보완했습니다.

## 7. 증거 자료 위치

| 구분 | 위치 |
|---|---|
| 실행 가이드 | `scripts/test-scenarios/s05-response-loss-idempotency/README.md` |
| Seed SQL | `scripts/test-scenarios/s05-response-loss-idempotency/seed/` |
| Verify SQL | `scripts/test-scenarios/s05-response-loss-idempotency/verify/` |
| Cleanup SQL | `scripts/test-scenarios/s05-response-loss-idempotency/cleanup/` |
| curl Timeout·전송 완료 출력 | 2026-09-17 로컬 터미널 출력 |
| 서비스 로그 | 실행 중 확인, 민감정보 제외 후 요약 기록 |

## 8. 후속 테스트 진행 결과

- 동일 Idempotency-Key에 다른 SeatHold를 넣은 요청은 `ReservationServiceTest`의 `동일한_멱등키를_다른_좌석에_사용하면_예외가_발생한다`로 검증함
- 결제 승인 재요청·중복 처리는 S03 검증 범위로 유지함
- Ticketing → Payment 생성 응답 유실 2차 Baseline 수행 완료
- 예매 의도 선커밋과 동일 멱등 요청 기반 복구 구현 완료
- 2차 Baseline과 같은 지연·Timeout 조건으로 개선 후 재검증

## 9. 2차 Baseline 및 개선 후 재테스트: Ticketing → Payment 생성 응답 유실

### 9.1 테스트 목적과 조건

Payment의 결제 생성 처리가 커밋된 뒤 Ticketing으로 돌아오는 응답만 유실된 상황에서 분리 트랜잭션의 최종 상태와 재요청 복구 가능 여부를 확인합니다.

| 항목 | 조건 |
|---|---|
| 요청 경로 | `curl → Ticketing:8082 → Toxiproxy:18083 → Payment:8083` |
| Payment 응답 지연 | downstream 5,000ms |
| Ticketing Payment Feign Read Timeout | 2,000ms |
| 요청 식별자 | 1차와 동일한 사용자·SeatHold·Idempotency-Key |
| 운영 코드 변경 | 없음 |

| 실행 정보 | 실제값 |
|---|---|
| 실행 Commit SHA | `7f5fa03e242d86d2e53a314799acb6bfad67ccd1` |
| 시작 시각 | `2026-09-17T22:57:49.8069799+09:00` |
| 실행 전 Reservation·ReservationSeat·Payment | 각 0건 |
| 실행 전 SeatHold | 1건, `HOLDING` |
| Payment URL | `http://toxiproxy:18083` |
| Feign Connect·Read Timeout | 1,000ms·2,000ms |
| Fixture 초기화 | Payment → Ticketing → Platform Cleanup 후 재생성 완료 |

### 9.2 사전 가설

아래는 실행 전 코드·트랜잭션 경계를 바탕으로 정리한 가설입니다. 실제 발견 결과와 구분해 기록합니다.

1. Payment에 `READY` 결제 1건이 커밋됩니다.
2. Ticketing은 Payment 응답 Timeout으로 Reservation·ReservationSeat·SeatHold 변경을 롤백합니다.
3. Payment가 Ticketing에 존재하지 않는 Reservation ID를 참조하는 고아 상태가 남습니다.
4. 동일 멱등 키 재요청은 새 Reservation ID와 기존 Payment의 Reservation ID 불일치로 복구에 실패합니다.

### 9.3 Baseline 실제 발견 결과

| 구분 | 실제 결과 |
|---|---|
| 최초 요청 시작 시각 | `2026-09-17T23:00:45.9528745+09:00` |
| 최초 HTTP 응답·경과 시간 | `504 Gateway Timeout`·2,199ms, `C-004` |
| Ticketing 롤백 여부 | Reservation·ReservationSeat 0건, SeatHold `HOLDING`, 롤백 확인 |
| Payment 커밋 여부 | `201 Created`·93ms, `READY` 1건 커밋 |
| Payment ID | `59c574cf-9c72-4498-a691-f597dc3ef9a1` |
| Payment의 Reservation ID | `33bba543-29c7-4d14-83cb-1fd69cafd107` |
| 고아·중복·상태 모순 | Ticketing에 해당 Reservation이 없지만 Payment만 남은 고아 상태 1건 |
| 재요청 시작 시각 | `2026-09-17T23:03:20.8392469+09:00` |
| 동일 요청 재전송 결과 | Payment `409`·52ms, Ticketing `503 R-008`·77ms, 복구 실패 |
| 재요청 후 DB 상태 | Reservation·ReservationSeat 0건, SeatHold `HOLDING`, 기존 Payment `READY` 1건으로 변화 없음 |
| 가설과 일치·불일치한 내용 | 가설 1~4 모두 일치 |
| 종료 시각 | `2026-09-17T23:09:13.7453120+09:00` |
| 환경 복원 | Payment 직접 URL, Feign Connect·Read Timeout 1,000ms·60,000ms 복원 |
| 정리 결과 | Payment 프록시 삭제(`404` 확인), Ticketing·Payment Fixture 0건 |

### 9.4 원인·구현 개선·재검증 기록

발견된 문제와 구현 개선을 인과관계로 연결하고, 개선 전·후를 같은 장애 조건으로 비교했습니다.

#### 개선 후 실행 정보

| 항목 | 실제값 |
|---|---|
| 실행 Commit SHA | `9754e62aff885eb6fba897f0aa59c8b807d6450a` |
| 실행 시작·종료 시각 | `2026-09-18T14:14:41.7741272+09:00` ~ `2026-09-18T14:22:12.5836459+09:00` |
| Payment 응답 지연·Feign Read Timeout | 5,000ms·2,000ms, Baseline과 동일 |
| 최초 요청 시작 시각 | `2026-09-18T14:18:33.4537195+09:00` |
| 재요청 시작 시각 | `2026-09-18T14:20:55.2395103+09:00` |
| Reservation ID | `f4576900-6444-4c11-9651-45f92d55875d` |
| Payment ID | `57e4ab58-50ed-41e4-b750-27f765a47906` |
| Fixture 초기화 | Payment → Ticketing → Platform Cleanup 후 재생성 완료 |
| 환경 복원 | Payment 직접 URL, Feign Connect·Read Timeout 1,000ms·60,000ms 복원 |
| 정리 결과 | Payment 프록시 삭제(`404` 확인), S05 Fixture 삭제 완료 |

| 항목 | 기록 내용 |
|---|---|
| 발견한 문제 | Payment 생성 성공 후 응답이 유실되면 Ticketing에 없는 Reservation을 참조하는 `READY` Payment가 남고, 동일 요청 재전송도 실패함 |
| 근본 원인 | 서비스별 트랜잭션 분리로 Payment만 커밋되고 Ticketing은 롤백됨. 재요청 시 Ticketing이 새 Reservation ID를 생성하여, Payment의 멱등 키 재사용 검증에서 기존 Reservation ID와 불일치함 |
| 구현 변경 | 예매 의도 저장과 SeatHold `RESERVED` 전환을 Payment 호출 전에 별도 트랜잭션으로 커밋함. Payment는 트랜잭션 밖에서 호출하고, 응답 검증·`paymentId` 연결·`PAYMENT_PROCESSING` 전환은 다시 별도 트랜잭션으로 처리함. 기존 `PAYMENT_PENDING` 멱등 요청은 같은 Reservation ID·사용자·금액·멱등 키로 Payment를 재호출하여 기존 Payment 결과를 받아 복구함 |
| 개선 전 결과 | 최초 Ticketing `504`·2,199ms, Payment `201`·93ms, 재요청 Ticketing `503`·77ms, Payment `409`·52ms, 고아 Payment 1건 |
| 개선 후 결과 | 최초 Ticketing `504`·2,522ms와 Payment `201`·415ms 후 Reservation `PAYMENT_PENDING` 1건과 Payment `READY` 1건이 동일 Reservation ID로 유지됨. 재요청은 Ticketing `200`·50ms, Payment `201`·12ms로 성공하고 기존 Payment ID를 연결함 |
| 최종 판정 | **PASS** — Reservation·ReservationSeat·SeatHold·Payment 각 1건, 중복·고아 데이터 0건, 기존 예매 복구 완료 |

#### 개선 전·후 핵심 비교

| 관측 항목 | 개선 전 | 개선 후 | 변화 |
|---|---|---|---|
| 최초 Timeout 후 Reservation | 0건, 전체 롤백 | 1건, `PAYMENT_PENDING` | 복구 기준점 확보 |
| 최초 Timeout 후 Payment | 1건, Ticketing에 없는 Reservation 참조 | 1건, Ticketing Reservation과 ID 일치 | 고아 Payment 1건 → 0건 |
| 최초 Timeout 후 SeatHold | `HOLDING` | `RESERVED` | 예매 처리 권한 유지 |
| 동일 요청 재전송 | Ticketing `503`, Payment `409` | Ticketing `200`, Payment `201` | 복구 실패 → 성공 |
| Ticketing 재요청 처리시간 | 77ms | 50ms | 27ms·35.1% 감소 |
| Payment 재요청 처리시간 | 52ms | 12ms | 40ms·76.9% 감소 |
| 최종 Reservation·Payment | Reservation 0건·Payment 1건 | Reservation 1건·Payment 1건 | 연결 정합성 회복 |
| 최종 중복·고아 데이터 | 고아 Payment 1건 | 0건 | 100% 제거 |

### 9.5 개선 구현의 복구 흐름

개선 전에는 Reservation 저장부터 Payment 생성 HTTP 호출까지 하나의 Ticketing 트랜잭션이었습니다. Payment가 `201 Created`로 커밋한 뒤 응답만 늦어져도 Ticketing은 Timeout 예외로 전체 롤백했으며, 재요청에서는 새로운 Reservation ID를 사용해 Payment의 기존 멱등 요청과 충돌했습니다.

개선 후에는 다음 순서로 처리합니다.

1. 첫 트랜잭션에서 Reservation을 `PAYMENT_PENDING`으로 저장하고 ReservationSeat 생성과 SeatHold `RESERVED` 전환을 커밋합니다.
2. DB 트랜잭션 밖에서 Payment 생성 API를 호출합니다.
3. 응답을 받으면 두 번째 트랜잭션에서 응답의 Reservation ID·금액·상태를 검증하고 `paymentId`와 `PAYMENT_PROCESSING` 상태를 저장합니다.
4. 응답을 받지 못하면 1번 결과를 유지합니다.
5. 같은 요청이 재전송되면 기존 `PAYMENT_PENDING` Reservation을 조회하고 같은 Reservation ID와 멱등 키로 Payment를 재호출합니다.
6. Payment가 기존 결제를 반환하면 두 번째 트랜잭션을 수행하여 예매 흐름을 복구합니다.

이 구조는 Payment 도메인의 API를 추가하거나 변경하지 않고, 기존 결제 생성 멱등 처리를 활용합니다.

### 9.6 자동 검증

| 검증 | 결과 |
|---|---|
| 기존 Reservation 단위 테스트와 신규 복구 경로 테스트 | 32개 PASS |
| Payment 호출 실패 후 `PAYMENT_PENDING` 유지 | 단위 테스트 PASS |
| 기존 `PAYMENT_PENDING` 동일 요청의 결제 연결 복구 | 단위 테스트 PASS |
| PostgreSQL 기반 선커밋·재요청 복구 통합 테스트 | Testcontainers 실행 PASS |
