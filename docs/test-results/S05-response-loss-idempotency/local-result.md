# S05 응답 유실 후 동일 요청 재전송 로컬 테스트 결과

## 1. 결과 요약

| 항목 | 내용                                                                                              |
|---|-------------------------------------------------------------------------------------------------|
| 시나리오 | S05 응답 유실 후 같은 요청 재전송                                                                           |
| 담당자 | 손유진                                                                                             |
| 관련 도메인 | Reservation, Seat Hold, Payment                                                                 |
| 테스트 유형 | 멱등성·네트워크 장애·데이터 정합성                                                                             |
| 실행 환경 | Windows Local Docker Compose                                                                    |
| 실행 회차 | 1차 Baseline                                                                                     |
| 테스트 결과 | **PASS**                                                                                         |
| 진행 상태 | **완료**                                                                                         |
| 관련 Issue | [#118 S05 응답 유실 멱등성 및 Payment 생성 복구 검증](https://github.com/tikitaka-team-8/tikitaka/issues/118) |
| 관련 PR | 작성 후 연결 예정                                                                                      |

최초 요청의 응답은 유실됐지만 서버 처리는 완료됐으며, 동일 요청 재전송에서 기존 결과를 반환했습니다. 최종 DB에서도 Reservation·ReservationSeat·Payment가 각 1건으로 유지되고 중복·고아 데이터가 없음을 확인했습니다.

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

## 8. 후속 테스트 계획

- 동일 Idempotency-Key에 다른 SeatHold를 넣은 요청은 `ReservationServiceTest`의 `동일한_멱등키를_다른_좌석에_사용하면_예외가_발생한다`로 검증함
- 결제 승인 재요청·중복 처리는 S03 검증 범위로 유지함
- Ticketing → Payment 생성 응답 유실 2차 Baseline 수행
- 실패 흐름이 확인되면 Payment 조회 또는 동일 멱등 요청 기반 복구 구현 및 동일 조건 재검증

## 9. 2차 Baseline 계획: Ticketing → Payment 생성 응답 유실

### 9.1 테스트 목적과 조건

Payment의 결제 생성 처리가 커밋된 뒤 Ticketing으로 돌아오는 응답만 유실된 상황에서 분리 트랜잭션의 최종 상태와 재요청 복구 가능 여부를 확인합니다.

| 항목 | 조건 |
|---|---|
| 요청 경로 | `curl → Ticketing:8082 → Toxiproxy:18083 → Payment:8083` |
| Payment 응답 지연 | downstream 5,000ms |
| Ticketing Payment Feign Read Timeout | 2,000ms |
| 요청 식별자 | 1차와 동일한 사용자·SeatHold·Idempotency-Key |
| 운영 코드 변경 | 없음 |

### 9.2 사전 가설

아래는 실행 전 코드·트랜잭션 경계를 바탕으로 정리한 가설입니다. 실제 발견 결과와 구분해 기록합니다.

1. Payment에 `READY` 결제 1건이 커밋됩니다.
2. Ticketing은 Payment 응답 Timeout으로 Reservation·ReservationSeat·SeatHold 변경을 롤백합니다.
3. Payment가 Ticketing에 존재하지 않는 Reservation ID를 참조하는 고아 상태가 남습니다.
4. 동일 멱등 키 재요청은 새 Reservation ID와 기존 Payment의 Reservation ID 불일치로 복구에 실패합니다.

### 9.3 실제 발견 결과

2차 Baseline 실행 후 다음 순서로 기록합니다.

| 구분 | 실제 결과 |
|---|---|
| 최초 HTTP 응답·경과 시간 | 실행 후 기록 예정 |
| Ticketing 롤백 여부 | 실행 후 기록 예정 |
| Payment 커밋 여부 | 실행 후 기록 예정 |
| 고아·중복·상태 모순 | 실행 후 기록 예정 |
| 동일 요청 재전송 결과 | 실행 후 기록 예정 |
| 가설과 일치·불일치한 내용 | 실행 후 기록 예정 |

### 9.4 원인·구현 개선·재검증 기록

실제 실패 흐름이 확인된 경우에만 아래 항목을 채웁니다. 발견된 문제와 구현 개선을 인과관계로 연결하고, 개선 전·후를 같은 장애 조건으로 비교합니다.

| 항목 | 기록 내용 |
|---|---|
| 발견한 문제 | Baseline 후 기록 |
| 근본 원인 | Baseline 후 분석 |
| 구현 변경 | 원인·담당 범위 합의 후 기록 |
| 개선 전 결과 | 2차 Baseline 수치 |
| 개선 후 결과 | 동일 조건 재테스트 후 기록 |
| 최종 판정 | 재테스트 후 PASS·FAIL 기록 |
