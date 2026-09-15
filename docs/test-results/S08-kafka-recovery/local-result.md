# S08 Kafka·Consumer 장애 복구 로컬 테스트 결과

## 기본 정보

- 시나리오: Kafka·Consumer 장애를 복구하는 운영자 수빈
- 담당자: 손유진
- 관련 도메인: Payment, Reservation, Seat Hold, Schedule Seat, Notification
- 테스트 유형: 이벤트 장애·복구, API 부하, 데이터·이벤트 정합성
- 실행 회차: 1차 예비 측정
- 실행 일시: 2026-09-15
- 실행 환경: Windows Local Docker Compose
- Branch: `fix/101-reservation-seat-payment-kafka-recovery`
- Commit SHA: `0b0d32d31091d578ed439f86c70206e5cf3c0cbb`
- Docker Image Tag: 로컬 Compose 빌드 이미지(`latest`)
- 적용 Profile: `docker`
- 관련 Issue: #101
- 진행 상태: 공식 Baseline 재측정 예정

### 측정 환경

| 항목 | 값 |
|---|---|
| CPU | Intel Core i9-14900HX, 24 Core / 32 Logical Processor |
| 시스템 메모리 | 31.7 GB |
| Docker 할당 CPU | 32 |
| Docker 할당 메모리 | 15.5 GB |
| 서비스 인스턴스 | 서비스별 1개 |
| DB·Kafka 위치 | 모든 서비스와 동일한 로컬 PC |
| Kafka | Apache Kafka 3.9.1 |
| PostgreSQL | PostgreSQL 16.15 |
| Grafana | Grafana 12.1.0 |
| 부하 도구 | k6 2.2.0, Windows Host에서 실행 |

테스트 전 최신 코드를 반영하고 충돌을 해결한 뒤 Ticketing Service와 Payment·Notification Service 이미지를 다시 빌드했다.

## 테스트 목적

Reservation 및 Notification Consumer가 중단된 동안에도 Payment와 Reservation 이벤트가 유실되지 않고 Kafka에 보존되는지 확인한다. Consumer 복구 후 미처리 이벤트가 모두 처리되고 Payment → Reservation → Notification의 최종 데이터가 중복 없이 일치하는지 검증한다.

## 테스트 자료 및 조건

- k6: [`scripts/k6/kafka-recovery.js`](../../../scripts/k6/kafka-recovery.js)
- 사용자 CSV: [`scripts/k6/data/s08-users.csv`](../../../scripts/k6/data/s08-users.csv)
- 결제 승인 CSV: [`scripts/k6/data/s08-payment-approvals.csv`](../../../scripts/k6/data/s08-payment-approvals.csv)
- 상태 준비·정리 SQL: `scripts/sql/seed-s08-*.sql`, `scripts/sql/cleanup-s08-*.sql`
- 상태 검증 SQL:
  - [`scripts/sql/verify-s08-ticketing.sql`](../../../scripts/sql/verify-s08-ticketing.sql)
  - [`scripts/sql/verify-s08-payment-notification.sql`](../../../scripts/sql/verify-s08-payment-notification.sql)

### 부하 조건

| 항목 | 값 |
|---|---:|
| 가상 사용자 | 100 VU |
| 사용자별 반복 | 10회 |
| 총 결제 승인 | 1,000건 |
| Ramp-up | 각 VU의 최초 요청을 0~990ms로 분산 |
| Consumer 중단 | Listener `autoStartup=false` |

100명의 사용자가 각각 서로 다른 결제 10건을 승인하도록 CSV와 SQL Fixture를 1:1로 연결했다. 실행 전 기존 S08 데이터를 정리하고 Payment `READY`, Reservation `PAYMENT_PROCESSING`, Seat Hold `RESERVED`, Schedule Seat `HELD` 상태로 각각 1,000건을 준비했다.

## 1차 예비 측정 결과

### 결제 승인 부하

| 지표 | 결과 |
|---|---:|
| 총 요청 | 1,000건 |
| 성공 | 1,000건(100%) |
| 실패율 | 0% |
| 처리량 | 267.07 req/s |
| 평균 응답시간 | 301.75 ms |
| 중앙값 | 246.87 ms |
| p90 | 556.90 ms |
| p95 | 773.35 ms |
| 최대 | 1.27 s |
| p99 | 미측정 |

k6 Threshold인 오류율 1% 미만, 결제 승인 성공률 99% 초과, p95 2초 미만을 모두 충족했다. 첫 실행 이후 스크립트에 p99 출력을 추가했으므로 공식 Baseline에서 재측정한다.

### Consumer 중단 중 상태

| 확인 대상 | 결과 |
|---|---|
| Payment | `APPROVED` 1,000건 |
| Payment Outbox | `PUBLISHED` 1,000건 |
| `ticketing-payment-group` Lag | 1,000 |
| Reservation | `PAYMENT_PROCESSING` 1,000건 유지 |
| Reservation Inbox·Outbox | 0건 |
| Seat Hold / Schedule Seat | `RESERVED` / `HELD` 각 1,000건 유지 |
| Notification / Notification Inbox | 0건 |

Consumer가 중단되어도 결제 승인과 Producer 발행은 정상적으로 완료됐고, 후속 상태 변경 없이 `payment-events`에 1,000건이 보존됐다.

### Ticketing Consumer 복구

| 항목 | 결과 |
|---|---:|
| 서비스 준비 완료 | 14:58:19.135 |
| 첫 Payment 이벤트 처리 완료 | 14:58:19.792 |
| 마지막 Payment 이벤트 처리 완료 | 14:58:29.097 |
| Consumer 처리 건수 | 1,000건 |
| 순수 처리 구간 | 약 9.31초 |
| 마지막 Reservation Outbox 발행 완료 | 14:58:30.030 |
| 준비 완료부터 Outbox 발행 완료 | 약 10.90초 |
| 재기동 포함 Outbox 발행 완료 | 약 18.51초 |
| `ticketing-payment-group` 최종 Lag | 0 |
| `notification-reservation-group` 대기 Lag | 1,000 |

Ticketing 검증 SQL에서 Reservation `CONFIRMED`, Reservation Inbox·Outbox, Seat Hold `CONFIRMED`, Schedule Seat `SOLD`가 각각 1,000건으로 확인됐다. Inbox·Outbox 중복은 없었다.

### Notification Consumer 복구

| 항목 | 결과 |
|---|---:|
| 서비스 준비 완료 | 15:08:17.955 |
| 첫 Reservation 이벤트 처리 완료 | 15:08:18.373 |
| 마지막 Reservation 이벤트 처리 완료 | 15:08:22.110 |
| Consumer 처리 건수 | 1,000건 |
| 순수 처리 구간 | 약 3.74초 |
| 준비 완료부터 처리 완료 | 약 4.16초 |
| ERROR 로그 | 0건 |
| `notification-reservation-group` 최종 Lag | 0 |

Payment·Notification 검증 SQL에서 Notification과 Notification Inbox가 각각 1,000건이고, 모든 Notification이 `UNREAD`이며 이벤트·예매 식별자 중복이 없는 것을 확인했다.

## 최종 데이터 정합성

| 대상 | 최종 결과 |
|---|---|
| Payment | `APPROVED` 1,000건 |
| Payment Outbox | `PUBLISHED` 1,000건 |
| Reservation | `CONFIRMED` 1,000건 |
| Reservation Inbox | 고유 이벤트 1,000건 |
| Reservation Outbox | `PUBLISHED` 1,000건 |
| Seat Hold | `CONFIRMED` 1,000건 |
| Schedule Seat | `SOLD` 1,000건 |
| Notification | `UNREAD` 1,000건 |
| Notification Inbox | 고유 이벤트 1,000건 |
| 최종 Consumer Lag | 두 Consumer Group 모두 0 |
| 중복·유실·상태 불일치 | 확인되지 않음 |

## 모니터링 결과 및 한계

- Grafana에서 Ticketing Consumer 복구 구간의 프로세스 CPU 최고점 약 22.2% 확인
- 결제 승인 구간 Payment·Notification Service CPU 최고점 약 9.12% 확인
- 부하 종료 약 15분 뒤의 `docker stats`는 안정 상태 스냅샷이므로 부하 중 Peak 자료로 사용하지 않음
- 현재 Grafana 대시보드에 Kafka Lag 패널이 없어 Kafbat UI로 Lag 확인
- 부하 실행 시간이 약 3.7초로 Prometheus 15초 Scrape 주기보다 짧아 HTTP RPS·응답시간 그래프가 충분히 표현되지 않음
- Consumer 중단 시간이 Grafana 점검을 포함해 약 15분으로 늘어났으며 정확한 장애 시작 시각은 미측정

## 1차 판정

- 테스트 결과: **PASS**
- 진행 상태: **공식 Baseline 및 실패 이벤트 재현 예정**
- 판정 근거:
  - Consumer 중단 중 두 Topic에 미처리 이벤트가 보존됨
  - Consumer 복구 후 Lag이 모두 0으로 정상화됨
  - Payment부터 Notification까지 1,000건의 최종 상태가 일치함
  - Inbox 기준 중복 데이터 및 이벤트 유실이 확인되지 않음
- 남은 확인 사항:
  - p99와 부하 중 자원 사용량을 포함한 통제된 공식 Baseline 재측정
  - 처리 불가능한 이벤트가 현재 Consumer와 동일 파티션의 후속 이벤트에 미치는 영향 측정
  - Retry·DLT 적용 후 동일 조건으로 실패 격리와 복구 결과 비교

## 2차 실패 격리 테스트 진행 배경

1차 테스트에서는 정상 이벤트만 존재할 때 Consumer 중단 중 메시지가 Kafka에 보존되고, 복구 후 Payment → Reservation → Notification의 최종 상태가 일치하는 것을 확인했다. 하지만 처리할 수 없는 이벤트가 정상 이벤트 사이에 들어온 경우의 재시도 횟수, 파티션 지연, 후속 이벤트 처리 여부와 실패 이벤트 보존 방식은 확인하지 못했다.

Retry·DLT 구현 효과를 판단하려면 적용 전 현재 오류 처리 동작을 먼저 측정해야 한다. 2차 테스트에서는 100 VU가 서로 다른 사용자 600명의 결제 1~2건을 총 1,000건 승인하고, 처리 불가능한 이벤트 10건을 정상 이벤트 사이에 분산한다. 이를 통해 실패 이벤트의 반복 처리 여부, Consumer Lag 정상화 시간, 후속 정상 이벤트 1,000건의 처리 결과와 DLT가 없는 현재 상태의 한계를 기록한다. 이후 같은 조건으로 Retry·DLT 적용 결과를 재측정하여 정상 처리량이 아닌 실패 격리와 운영 복구 가능성의 개선 여부를 비교한다.
