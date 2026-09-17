# S05 응답 유실 후 동일 요청 재전송 로컬 테스트 결과

## 1. 결과 요약

| 항목 | 내용                                                                                              |
|---|-------------------------------------------------------------------------------------------------|
| 시나리오 | S05 응답 유실 후 같은 요청 재전송                                                                           |
| 담당자 | 손유진                                                                                             |
| 관련 도메인 | Reservation, Seat Hold, Payment                                                                 |
| 테스트 유형 | 멱등성·네트워크 장애·데이터 정합성                                                                             |
| 실행 환경 | Windows Local Docker Compose                                                                    |
| 실행 회차 | 1차 Baseline 예정                                                                                  |
| 테스트 결과 | **미실행**                                                                                         |
| 진행 상태 | **진행 중**                                                                                        |
| 관련 Issue | [#118 S05 응답 유실 멱등성 및 Payment 생성 복구 검증](https://github.com/tikitaka-team-8/tikitaka/issues/118) |
| 관련 PR | 작성 후 연결 예정                                                                                      |

현재 문서는 테스트 환경과 판정 기준을 먼저 기록한 상태입니다. Baseline 실행 전에는 PASS 또는 FAIL로 판정하지 않습니다.

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
| Postman | 회원가입·로그인, 최초 예매 요청, 동일 요청 재전송 |
| Toxiproxy 2.12.0 | `Postman → Gateway` 구간의 downstream 응답 10초 지연 |
| SQL Seed | 예매 생성 직전의 공연·회차·ScheduleSeat·SeatHold 상태 준비 |
| SQL Verify | 멱등 키 기준 Reservation·Payment·Seat·Outbox 집계 |
| SQL Cleanup | 세 DB의 S05 전용 데이터만 FK 의존 순서에 맞춰 정리 |
| 서비스 로그 | 최초 요청 도달과 서버 처리 결과 추적 |

```text
Postman → Toxiproxy:18000 → Gateway:8000 → Ticketing → Payment
```

상세 재현 순서와 명령은 [S05 로컬 테스트 가이드](../../../scripts/test-scenarios/s05-response-loss-idempotency/README.md)에 분리했습니다.

## 4. 공통 실행 정보

| 항목 | 값                                        |
|---|------------------------------------------|
| 실행 일시 | 실행 후 기록 예정                               |
| 담당자 | 손유진                                      |
| 실행 환경 | Local                                    |
| Branch | `test/118-s05-response-loss-idempotency` |
| 실행 대상 Commit SHA | 실행 직전 기록 예정                              |
| 결과 기록 Commit SHA | 결과 커밋 후 기록 예정                            |
| Docker Image Tag | Local Compose image / 실행 후 기록 예정         |
| 적용 Profile | `docker`                                 |
| 서비스 인스턴스 수 | 서비스별 1개                                  |
| DB·Kafka 위치 | 서비스와 동일한 로컬 PC의 Docker Compose           |
| 테스트 데이터 | S05 전용 SQL Fixture, 실행 전 초기화             |
| 인증 정보 | Postman 회원가입·로그인으로 생성, 공유 문서에 미기록        |
| 관련 대시보드 | 해당 없음                                    |

### 실행 머신과 도구 버전

| 항목 | 값 |
|---|---|
| CPU | 실행 후 기록 예정 |
| 시스템 메모리 | 실행 후 기록 예정 |
| Docker 할당 CPU·메모리 | 실행 후 기록 예정 |
| PostgreSQL | `16.15` |
| Postman | 실행 후 기록 예정 |
| Docker / Docker Compose | 실행 후 기록 예정 |
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
| Postman Timeout | 3,000ms |
| 재요청 횟수 | 1회 |

## 6. PASS 기준

1. 최초 Postman 요청은 Client Timeout으로 종료됩니다.
2. 재요청 전에 서버가 Reservation과 Payment 생성을 완료한 사실을 SQL과 로그로 확인할 수 있습니다.
3. 동일 요청 재전송은 `200 OK`와 기존 Reservation·Payment ID를 반환합니다.
4. Reservation·ReservationSeat·Payment는 각각 1건만 존재합니다.
5. Reservation과 Payment가 서로 같은 Reservation ID와 Payment ID로 연결됩니다.
6. SeatHold는 1건이며 `RESERVED` 상태입니다.
7. 승인 전 단계이므로 PaymentTransaction·Payment Outbox·Reservation Outbox는 0건입니다.
8. 중복·유실·고아 데이터와 상태 모순이 없습니다.

## 7. 1차 Baseline 실행 결과

### 최초 요청 관찰

| 항목 | 실제 결과 |
|---|---|
| 최초 요청 시작 시각 | 실행 후 기록 예정 |
| Postman 관찰 결과 | 실행 후 기록 예정 |
| Client Timeout 경과 시간 | 실행 후 기록 예정 |
| 서버 최초 처리 완료 여부 | 실행 후 기록 예정 |

### 재요청 전 DB 상태

| 항목 | 기대값 | 실제값 |
|---|---:|---:|
| Reservation | 1 | 실행 후 기록 예정 |
| ReservationSeat | 1 | 실행 후 기록 예정 |
| Payment | 1 | 실행 후 기록 예정 |
| SeatHold `RESERVED` | 1 | 실행 후 기록 예정 |
| PaymentTransaction | 0 | 실행 후 기록 예정 |
| Payment Outbox | 0 | 실행 후 기록 예정 |
| Reservation Outbox | 0 | 실행 후 기록 예정 |

### 동일 요청 재전송 결과

| 항목 | 기대값 | 실제값 |
|---|---|---|
| HTTP 상태 | `200 OK` | 실행 후 기록 예정 |
| Reservation ID | 최초 처리 ID와 동일 | 실행 후 기록 예정 |
| Payment ID | 최초 처리 ID와 동일 | 실행 후 기록 예정 |
| Reservation 상태 | `PAYMENT_PROCESSING` | 실행 후 기록 예정 |

### 재요청 후 최종 DB 상태

| 항목 | 기대값 | 실제값 |
|---|---:|---:|
| Reservation | 1 | 실행 후 기록 예정 |
| 고유 Payment 연결 | 1 | 실행 후 기록 예정 |
| ReservationSeat | 1 | 실행 후 기록 예정 |
| Payment | 1 | 실행 후 기록 예정 |
| 고유 Reservation 연결 | 1 | 실행 후 기록 예정 |
| 중복·고아 데이터 | 0 | 실행 후 기록 예정 |

## 8. 발견 문제와 원인

Baseline 실행 후 기록 예정입니다. 현재 동작이 PASS이면 불필요한 운영 코드 변경 없이 재현 자료와 자동 테스트만 보강합니다. FAIL이면 SQL·로그 증거를 기준으로 담당 범위의 원인을 분리하고, 합의한 복구 흐름을 구현한 뒤 동일 조건으로 재검증합니다.

## 9. 개선 내용과 동일 조건 재테스트

Baseline에서 문제를 발견한 경우에만 작성합니다.

| 비교 항목 | 개선 전 | 개선 후 |
|---|---|---|
| 장애 조건 | downstream 10초 지연 | 동일 조건 유지 |
| Postman Timeout | 3초 | 동일 조건 유지 |
| Fixture와 멱등 키 | S05 고정 데이터 | 동일 데이터 유지 |
| 재요청 횟수 | 1회 | 동일 조건 유지 |
| 최종 결과 | Baseline 후 기록 | 재테스트 후 기록 |

## 10. 증거 자료 위치

| 구분 | 위치 |
|---|---|
| 실행 가이드 | `scripts/test-scenarios/s05-response-loss-idempotency/README.md` |
| Seed SQL | `scripts/test-scenarios/s05-response-loss-idempotency/seed/` |
| Verify SQL | `scripts/test-scenarios/s05-response-loss-idempotency/verify/` |
| Cleanup SQL | `scripts/test-scenarios/s05-response-loss-idempotency/cleanup/` |
| Postman Timeout·응답 캡처 | 실행 후 기록 예정 |
| 서비스 로그 | 실행 중 확인, 민감정보 제외 후 요약 기록 |

## 11. 후속 테스트 계획

- 동일 Idempotency-Key에 다른 SeatHold를 넣은 요청 거절
- 결제 승인 응답 유실 후 동일 `paymentId` 재요청 최소 확인
- Ticketing → Payment 생성 응답 유실 Baseline
- 실패 흐름이 확인되면 Payment 조회 또는 동일 멱등 요청 기반 복구 구현 및 동일 조건 재검증
