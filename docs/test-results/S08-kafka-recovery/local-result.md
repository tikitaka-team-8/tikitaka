# S08 Kafka·Consumer 장애 복구 로컬 테스트 결과

## 기본 정보

- 시나리오: Kafka·Consumer 장애를 복구하는 운영자 수빈
- 담당자: 손유진
- 관련 도메인: Payment, Reservation, Seat Hold, Schedule Seat, Notification
- 테스트 유형: 이벤트 장애·복구, API 부하, 데이터·이벤트 정합성
- 실행 회차: 1차 예비 측정, 2차 실패 격리 Baseline 및 3차 Retry·DLT 적용 후 비교
- 실행 일시: 2026-09-15
- 실행 환경: Windows Local Docker Compose
- Branch: `fix/101-reservation-seat-payment-kafka-recovery`
- Commit SHA: Baseline `eaff507f6b310f3aaffe8ddca6bf97541cd1aa55`, 개선 후 `81308ec594ce8d69f91c520d30761db76ce573ea`
- Docker Image Tag: 로컬 Compose 빌드 이미지(`latest`)
- 적용 Profile: `docker`
- 관련 Issue: #101
- 진행 상태: Retry·DLT 적용 후 혼합 실패 격리 비교 및 재시도 후 성공 보충 검증 완료

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
- 상태 준비·정리 SQL: [`scripts/sql/s08-kafka-recovery/`](../../../scripts/sql/s08-kafka-recovery/README.md)
- 상태 검증 SQL:
  - [`scripts/sql/s08-kafka-recovery/_shared/verify-ticketing.sql`](../../../scripts/sql/s08-kafka-recovery/_shared/verify-ticketing.sql)
  - [`scripts/sql/s08-kafka-recovery/_shared/verify-payment-notification.sql`](../../../scripts/sql/s08-kafka-recovery/_shared/verify-payment-notification.sql)
- 실패 격리 k6: [`scripts/k6/kafka-failure-recovery.js`](../../../scripts/k6/kafka-failure-recovery.js)
- 실패 격리 사용자 CSV: [`scripts/k6/data/s08-failure-users.csv`](../../../scripts/k6/data/s08-failure-users.csv)
- 실패 격리 결제 CSV: [`scripts/k6/data/s08-failure-payment-approvals.csv`](../../../scripts/k6/data/s08-failure-payment-approvals.csv)
- 실패 이벤트 주입: [`scripts/kafka/s08/inject-failure-events.sh`](../../../scripts/kafka/s08/inject-failure-events.sh)
- 정상 부하·실패 주입 동시 실행: [`scripts/kafka/s08/run-payment-failure-load.sh`](../../../scripts/kafka/s08/run-payment-failure-load.sh)

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

Retry·DLT 구현 효과를 판단하려면 적용 전 현재 오류 처리 동작을 먼저 측정해야 한다. 2차 테스트에서는 100 VU가 서로 다른 사용자 600명의 결제 1~2건을 총 1,000건 승인하고, 처리 불가능한 이벤트 10건을 함께 발행한다. 이를 통해 실패 이벤트의 반복 처리 여부, Consumer Lag 정상화 시간, 후속 정상 이벤트 1,000건의 처리 결과와 DLT가 없는 현재 상태의 한계를 기록한다. 이후 같은 조건으로 Retry·DLT 적용 결과를 재측정하여 정상 처리량이 아닌 실패 격리와 운영 복구 가능성의 개선 여부를 비교한다.

## 2차 실패 격리 Baseline 결과

### 실행 조건과 워밍업

| 항목 | 값 |
|---|---|
| 가상 사용자 | 100 VU |
| 사용자 데이터 | 600명 |
| 사용자별 승인 | 1건 또는 2건 |
| 총 결제 승인 | 1,000건 |
| k6 반복 | VU별 6회, 총 600 iteration |
| Payment 실패 이벤트 | `UNSUPPORTED_PAYMENT_EVENT` 10건 |
| Reservation 실패 이벤트 | `UNSUPPORTED_RESERVATION_EVENT` 10건 |
| Kafka Partition | Topic별 1개 |
| Consumer 시작 상태 | Reservation·Notification 모두 중단 |

첫 실행에서 Git Bash가 Kafka 컨테이너 내부 `/opt` 경로를 Windows 경로로 변환하여 실패 이벤트 Producer 실행이 중단됐다. 이 실행의 정상 승인 1,000건을 워밍업으로 간주하고 Consumer Offset과 DB를 초기화한 뒤 공식 Baseline을 재실행했다. 공식 측정은 서비스 재시작 없이 진행했으며 Retry·DLT 적용 후에도 동일한 워밍업과 초기화 절차를 사용한다.

### 결제 승인 부하

| 지표 | 결과 |
|---|---:|
| 총 요청 | 1,000건 |
| 성공 | 1,000건(100%) |
| 실패율 | 0% |
| 처리량 | 640.48 req/s |
| 평균 응답시간 | 92.29 ms |
| 중앙값 | 83.21 ms |
| p90 | 172.73 ms |
| p95 | 204.88 ms |
| p99 | 275.48 ms |
| 최대 | 393.54 ms |
| 실행시간 | 약 1.6초 |

k6 Threshold인 오류율 1% 미만, 결제 승인 성공률 99% 초과, p95 2초 미만을 모두 충족했다. 부하 시간이 Prometheus Scrape 주기보다 짧으므로 HTTP 성능은 k6 요약값을 기준으로 사용한다.

### Payment 실패 이벤트 배치

`payment-events`의 공식 측정 입력 범위는 Offset 2001~3010이며 실패 이벤트는 다음 위치에 기록됐다.

```text
정상 200건: 2001~2200
실패 5건: 2201~2205
정상 200건: 2206~2405
실패 5건: 2406~2410
정상 600건: 2411~3010
```

실패 이벤트 뒤에 정상 이벤트가 존재하므로 동일 파티션에서 오류 처리 후 정상 처리가 재개되는지 확인할 수 있었다. Reservation Consumer 중단 중 `ticketing-payment-group` Lag은 1,010까지 증가했다.

### Ticketing Consumer 복구

| 항목 | 결과 |
|---|---:|
| 시작 Offset | 2001 |
| 종료 Offset | 3011 |
| 정상 처리 | 1,000건 |
| 실패 이벤트 | 10건 |
| 정상 최초 처리 | 18:21:30.856 |
| 정상 최종 처리 | 18:21:39.786 |
| 정상 처리 구간 | 약 8.93초 |
| Consumer 할당부터 정상 처리 완료 | 약 9.12초 |
| Outbox 최초 발행 | 18:21:33.799 |
| Outbox 최종 발행 | 18:21:40.792 |
| Outbox 발행 구간 | 약 6.99초 |
| 최종 Lag | 0 |

기본 `DefaultErrorHandler`는 실패 이벤트마다 최초 처리 1회와 재시도 9회를 대기 없이 수행했다. 로그의 `FixedBackOff`는 `interval=0`, 총 시도는 이벤트당 10회였다. Offset 2201~2205 처리에는 약 0.60초, Offset 2406~2410 처리에는 약 0.56초가 걸렸다. 실패 이벤트는 Inbox에 저장되지 않았고 DLT도 없어 재시도 소진 뒤 원본을 별도로 조회하거나 재처리할 수 없었다.

첫 번째 실패 구간 뒤 정상 200건, 두 번째 실패 구간 뒤 정상 600건이 처리됐고 최종 Offset 3011에 도달했다. 처리 불가능한 이벤트가 파티션을 영구 중단시키지는 않았으나 실패 원본은 보존되지 않았다.

### Reservation 실패 이벤트 배치

Notification 구간은 정상 이벤트와 실패 이벤트가 다음 순서로 기록됐다.

```text
정상 RESERVATION_CONFIRMED 1,000건: 2011~3010
실패 UNSUPPORTED_RESERVATION_EVENT 10건: 3011~3020
```

마지막 정상 Outbox 발행은 18:21:40.792, 첫 실패 이벤트 발행은 18:22:08.323으로 약 27.5초 차이가 있었다. 실패 주입을 정상 Outbox 발행 완료 후 실행하여 실패 이벤트가 Topic 마지막에 연속 배치됐다. 따라서 이 구간은 연속 실패의 누적 지연은 측정하지만 실패 이벤트 뒤 정상 이벤트의 대기 시간은 측정하지 않는다.

### Notification Consumer 복구

| 항목 | 결과 |
|---|---:|
| 시작 Offset | 2011 |
| 종료 Offset | 3021 |
| 정상 처리 | 1,000건 |
| 실패 이벤트 | 10건 |
| 정상 최초 처리 | 19:14:39.434 |
| 정상 최종 처리 | 19:14:42.955 |
| 정상 처리 구간 | 약 3.52초 |
| 실패 처리 구간 | 약 49.67초 |
| Consumer 할당부터 최종 Lag 0 | 약 53.42초 |
| 관측 Lag 변화 | 1,010 → 7 → 1 → 0 |
| 최종 Lag | 0 |

Notification도 실패 이벤트마다 총 10회 시도한 뒤 다음 Offset으로 진행했다. 로그의 명시적 `FixedBackOff interval`은 0이지만 실제 재시도는 Consumer poll의 영향을 받아 약 0.5초 간격으로 관측됐고, 실패 이벤트 한 건당 약 5초가 소요됐다. 실패 10건은 약 49.67초 동안 파티션을 순차 점유했으며 DLT 없이 폐기됐다.

### 최종 데이터 정합성

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
| Consumer Group | 두 Group 모두 활성 Consumer 1개, Lag 0 |
| 중복·고아 데이터 | 0건 |

정상 이벤트는 Payment부터 Notification까지 유실 없이 처리됐고 Inbox·Outbox 및 도메인 데이터의 중복은 없었다. 처리 불가능한 이벤트는 도메인 데이터에 영향을 주지 않았지만 별도 보존되지 않았다.

### 자원 관측

- Ticketing Consumer 처리 구간 Grafana CPU 최고 관측값 약 12.5%
- Notification 처리 구간의 CPU·Heap 변화는 Grafana 캡처로 보존했으나 정확한 Peak 수치는 측정하지 않음
- 종료 후 스냅샷 시각: 2026-09-15 19:23:18 KST

| 컨테이너 | CPU | 메모리 |
|---|---:|---:|
| Ticketing Service | 0.24% | 527 MiB |
| Payment·Notification Service | 0.39% | 416.5 MiB |
| Kafka | 0.83% | 1.039 GiB |

종료 후 `docker stats`는 부하 중 Peak가 아닌 안정 상태 참고값으로만 사용한다.

### 2차 판정 및 후속 비교 기준

- 테스트 결과: **PASS**
- 진행 상태: **Retry·DLT 적용 전 Baseline 완료**
- 확인된 문제:
  - 복구 불가능한 이벤트도 이벤트당 총 10회 처리
  - Notification 연속 실패 10건이 파티션을 약 49.67초 점유
  - 재시도 소진 이벤트를 별도로 보존하지 않아 운영자 조회·재처리 불가
- Retry·DLT 적용 후 비교 항목:
  - 2초 간격의 합의된 유한 재시도 횟수 준수 여부
  - 재시도 소진 이벤트의 DLT 10건 보존 여부
  - 정상 이벤트, Inbox·Outbox 및 최종 도메인 상태 정합성
  - Consumer Lag 정상화 시간과 실패 이벤트당 파티션 점유 시간
  - 같은 워밍업·Fixture·Offset 배치 조건 유지 여부
- 추가 혼합 검증:
  - Ticketing Outbox 발행 중 Reservation 실패 이벤트 주입 시작
  - 실제 Offset 분포를 확인하여 실패 뒤 정상 Notification 이벤트가 존재하는지 검증

## 3차 Retry·DLT 적용 후 비교 계획

2차의 `UNSUPPORTED_*` 이벤트는 현재 정책상 재시도 불가 대상이지만, 당시에는 예외 분류가 없어 모든 실패를 이벤트당 총 10회 처리했다. 3차에서는 동일한 실행 환경과 정상 결제 승인 1,000건, Consumer별 실패 이벤트 10건을 유지하면서 새 정책의 두 경로를 함께 검증한다.

| 실패 유형 | 건수 | 기대 처리 |
|---|---:|---|
| 비재시도 오류 | 5건 | 재시도 없이 즉시 DLT 이동 |
| 재시도 가능 DB 오류 | 5건 | 2초 간격, 최초 포함 총 3회 시도 후 DLT 이동 |

- 기존 총 처리 시도 `100회(10건 × 10회)`와 개선 후 `20회(5건 × 1회 + 5건 × 3회)` 비교
- 실패 이벤트를 정상 이벤트 사이에 고정 Seed로 분산하고 실패 Offset 뒤 정상 Offset의 진행 여부 확인
- DLT 10건 보존, 최종 Lag 0, 정상 데이터 1,000건 및 Inbox·Outbox 중복 없음 확인
- Baseline에서는 예외 유형과 관계없이 모든 실패를 동일하게 10회 처리했으므로, 개선 후에는 실패 유형을 분리하여 새 예외 분류 정책 자체의 효과를 검증
- 별도 보충 검증에서 재시도 가능 오류가 중간에 해소되어 최종 성공하는 이벤트 1건 확인 예정

## 3차 Retry·DLT 적용 후 비교 결과

### 실행 조건과 초기 상태

- 준비 확인 시각: `2026-09-15T22:04:36.4902586+09:00`
- 부하 테스트 시작 시각: `2026-09-15T22:09:58.8136213+09:00`
- 최종 확인 종료 시각: `2026-09-15T22:24:40.3752153+09:00`
- Ticketing·Payment·Notification 데이터: 각 정상 흐름 1,000건 Fixture 재초기화
- Listener 초기 상태: `ticketing-payment-group`, `notification-reservation-group` 모두 Member 0, Lag 0
- Docker 이미지: Ticketing Service와 Payment·Notification Service 모두 재빌드 후 컨테이너 재생성
- 측정 환경: Baseline과 동일한 Windows Local Docker Compose, 머신·Docker 자원·서비스 인스턴스 수 유지
- 시작 전 Topic 메시지 수: `payment-events` 4,021건, `reservation-events` 4,031건, DLT Topic 없음

초기 SQL 검증에서 Payment 1,000건은 모두 `READY`, Reservation 1,000건은 모두 `PAYMENT_PROCESSING`, SeatHold 1,000건은 모두 `RESERVED`, ScheduleSeat 1,000건은 모두 `HELD`였다. 각 Inbox·Outbox와 Notification은 0건이었고 중복 조회 결과도 0행이었다.

### 정상 결제 승인 부하와 실패 이벤트 분산

`run-payment-retry-dlt-load.sh`로 정상 결제 승인 1,000건과 혼합 실패 이벤트 10건을 동시에 발행했다. 실패 이벤트는 고정 Seed의 5초 발행 창에서 비재시도 5건과 재시도 가능 5건을 번갈아 구성했다.

| 지표 | 결과 |
|---|---:|
| HTTP 요청 | 1,000건 |
| 성공률 | 100.00% |
| HTTP 실패율 | 0.00% |
| 처리량 | 341.98 req/s |
| 평균 응답시간 | 338.61 ms |
| 중앙값 | 310.27 ms |
| p90 | 580.84 ms |
| p95 | 666.94 ms |
| p99 | 889.61 ms |
| 최대 응답시간 | 1.29 s |

`payment-events`의 신규 Offset 범위는 4,021~5,030이었다. 실패 이벤트는 Offset 4,021~4,025와 4,207~4,211에 배치됐고, 두 구간 사이에 정상 이벤트 181건, 마지막 실패 뒤에 정상 이벤트 819건이 존재했다. 따라서 Baseline처럼 실패 10건이 Topic 끝에만 몰리지 않았으며 후속 정상 Offset 진행을 함께 검증할 수 있는 분포가 형성됐다.

### Ticketing Consumer 복구

- 복구 명령 시작: `2026-09-15T22:12:59.6967984+09:00`
- 실제 첫 처리 로그: `2026-09-15 22:13:23.855`
- 마지막 정상 처리 로그: `2026-09-15 22:13:51.804`
- 명령 시작부터 처리 완료: 약 52.11초
- 실제 Kafka 처리 구간: 약 27.95초
- 정상 `PaymentSucceededEvent`: 1,000건 처리
- 비재시도 오류: 5건, 최초 실패 후 즉시 DLT 전송
- 재시도 가능 DB 오류: 5건, 이벤트당 최초 포함 3회 처리 후 DLT 전송
- 재시도 가능 오류 로그: 총 15회(`5건 × 3회`), 각 이벤트에서 약 2초 간격 확인
- `payment-events.DLT`: 10건
- 최종 `ticketing-payment-group`: Member 1, Lag 0

명령 기준 시간에는 약 24초의 컨테이너 기동과 Consumer Group 할당이 포함되므로 Retry 정책의 처리 시간은 실제 로그 구간 약 27.95초로 구분한다. `reservation-events`의 신규 Offset 범위는 4,031~5,040이었으며 실패 이벤트는 4,031~4,034, 4,087, 4,217~4,221에 분산됐다. 마지막 실패 뒤에도 정상 이벤트 819건이 존재했다.

Ticketing 최종 SQL 결과는 Reservation `CONFIRMED` 1,000건, Reservation Inbox 1,000건, Outbox `PUBLISHED` 1,000건이었다. SeatHold `CONFIRMED`와 ScheduleSeat `SOLD`도 각각 1,000건이었으며 이벤트와 Reservation 식별자 중복은 없었다.

### Notification Consumer 복구

- 복구 명령 시작: `2026-09-15T22:19:53.3405886+09:00`
- 실제 첫 처리 로그: `2026-09-15 22:20:13.371`
- 마지막 정상 처리 로그: `2026-09-15 22:20:37.150`
- 명령 시작부터 처리 완료: 약 43.81초
- 실제 Kafka 처리 구간: 약 23.78초
- Lag 관찰: 1,010 → 820 → 0
- 정상 `ReservationConfirmedEvent`: 1,000건 처리
- 비재시도 오류: 5건, 최초 실패 후 즉시 DLT 전송
- 재시도 가능 DB 오류: 5건, 이벤트당 최초 포함 3회 처리 후 DLT 전송
- 재시도 가능 오류 로그: 총 15회(`5건 × 3회`), 각 이벤트에서 약 2초 간격 확인
- `reservation-events.DLT`: 10건
- 최종 `notification-reservation-group`: Member 1, Lag 0

명령 기준 시간에는 약 20초의 컨테이너 기동과 Consumer Group 할당이 포함됐다. Notification 최종 SQL 결과는 Payment `APPROVED`와 Payment Outbox `PUBLISHED`가 각각 1,000건, Notification과 Notification Inbox가 각각 1,000건이었다. 이벤트와 Reservation 식별자는 모두 1,000개로 중복이 없었다.

### 자원 사용과 개선 전후 비교

| 구분 | Ticketing Service | Payment·Notification Service |
|---|---:|---:|
| 복구 구간 CPU 최대 | 약 2.02% | 약 0.78% |
| 복구 구간 JVM Heap 최대 | 약 127.7 MiB | 약 92.4 MiB |

Grafana의 Ticketing Heap 172 MiB 관찰값은 복구 이후 Outbox 발행과 대기 관찰 구간까지 포함한 값이므로 위 표의 실제 복구 구간 최대값과 분리한다. 종료 후 `docker stats --no-stream`은 Ticketing 0.41% / 520.3 MiB, Payment·Notification 0.51% / 392.3 MiB, Kafka 1.19% / 1.047 GiB였으며 부하 Peak가 아닌 안정 상태 참고값이다.

| 비교 항목 | Retry·DLT 적용 전 | Retry·DLT 적용 후 |
|---|---:|---:|
| 실패 이벤트 | 10건 | 10건(비재시도 5건 + 재시도 가능 5건) |
| 총 처리 시도 | 100회 | 20회 |
| Notification 실패 포함 처리 구간 | 약 49.67초 | 약 23.78초 |
| 실패 이벤트 보존 | 없음 | DLT 10건 |
| 최종 Lag | 0 | 0 |

Notification의 실제 처리 구간은 약 25.89초, 약 52.1% 감소했다. Baseline에서는 예외 유형과 관계없이 실패 10건 모두를 동일하게 10회 처리했지만, 개선 후에는 같은 정상 이벤트 1,000건과 실패 이벤트 10건 조건에서 복구 불가능한 오류 5건은 즉시 격리하고 재시도 가능 오류 5건만 최대 3회 처리했다. 실패 유형을 구분한 것은 비교 조건의 한계가 아니라 불필요한 재시도를 제거하는 이번 정책 개선의 핵심이며, 그 결과 총 처리 시도가 100회에서 20회로 감소하고 모든 최종 실패가 DLT에 보존됐다.

### 개선 수치 요약

| 지표 | 개선 전 | 개선 후 | 개선 결과 |
|---|---:|---:|---:|
| 실패 이벤트 수 | 10건 | 10건 | 동일 |
| 실패 이벤트 총 처리 시도 | 100회 | 20회 | 80회 감소, 80.0% 절감 |
| 이벤트당 최대 처리 시도 | 10회 | 비재시도 1회 / 재시도 가능 3회 | 최대 7회 감소 |
| Notification 실패 포함 실제 처리 구간 | 약 49.67초 | 약 23.78초 | 약 25.89초 감소, 52.1% 단축 |
| 최종 실패 보존 | 0건 | DLT 10건 | 유실 상태에서 100% 보존으로 개선 |
| 정상 이벤트 처리 | 1,000건 | 1,000건 | 성공률 100% 유지 |
| 최종 Consumer Lag | 0 | 0 | 정상화 유지 |
| Inbox·Outbox 중복 | 0건 | 0건 | 정합성 유지 |

동일한 정상 이벤트 1,000건과 실패 이벤트 10건을 처리하면서 실패 처리 시도는 100회에서 20회로 80.0% 줄었고, Notification의 실제 처리 시간은 약 52.1% 단축됐다. 동시에 개선 전에는 남지 않던 최종 실패 이벤트 10건을 DLT에 모두 보존했으며, 정상 이벤트 성공률 100%, 최종 Lag 0, Inbox·Outbox 중복 0건을 유지했다.

### 3차 판정

- 테스트 결과: **PASS**
- 정상 승인 1,000건과 두 Consumer의 최종 데이터 정합성 확인
- 실패 이벤트가 정상 이벤트 사이에 분산되고 마지막 실패 뒤 정상 Offset 진행 확인
- 비재시도 5건의 즉시 DLT와 재시도 가능 5건의 2초 간격 총 3회 처리 확인
- Consumer별 DLT 10건, 최종 Lag 0, Inbox·Outbox 중복 없음 확인
- 재시도 가능 오류가 두 번 발생한 뒤 세 번째 처리에서 성공하는 단일 이벤트는 아래 보충 테스트에서 추가 검증

## 재시도 후 최종 성공 보충 검증 결과

혼합 실패 테스트는 재시도 가능 오류가 총 3회 모두 실패하여 DLT로 이동하는 경로를 검증했다. 보충 테스트에서는 동일한 Retry 정책에서 일시적 DB 오류가 1·2차 처리 후 해소됐을 때 세 번째 처리로 정상 복구되는지 단일 이벤트로 확인했다. 전체 Fixture를 다시 적재하지 않고 1번 Reservation과 관련 Inbox·Outbox·Notification만 초기 상태로 되돌렸으며, 기존 DLT 결과는 유지했다.

### 실행 조건

- 기록 시작 시각: `2026-09-15T22:44:25.5173677+09:00`
- 단일 Payment 이벤트 발행 시각: `2026-09-15 22:45:50 KST`
- 기록 종료 시각: `2026-09-15T22:48:29.5586314+09:00`
- Payment Event ID: `f3000000-0000-0000-0000-000000000001`
- Reservation ID: `81000000-0000-0000-0000-000000000001`
- 실패 주입: Consumer별 최초 2회에 PostgreSQL `SQLSTATE 40001` 발생, 세 번째 처리 허용
- 시작 전 DLT: `payment-events.DLT` 10건, `reservation-events.DLT` 10건

Setup 결과는 Ticketing의 기존 Inbox·Outbox 각 1건 삭제, Reservation·SeatHold·ScheduleSeat 각 1건 초기화와 두 Consumer의 일시적 실패 트리거 생성을 모두 정상 완료했다. Notification도 기존 Inbox·Notification 각 1건을 삭제하고 일시적 실패 트리거를 생성했다.

### Retry 시간과 최종 성공

| Consumer | 1차 실패 | 2차 실패 | 3차 성공 | 실패 간격 | 최초 실패부터 성공 |
|---|---|---|---|---:|---:|
| Ticketing | 22:45:51.308 | 22:45:53.317 | 22:45:55.328 | 약 2.009초 / 2.011초 | 약 4.020초 |
| Notification | 22:45:56.125 | 22:45:58.133 | 22:46:00.141 | 약 2.008초 / 2.008초 | 약 4.016초 |

Ticketing은 세 번째 시도에서 `PaymentSucceededEvent`를 `statusChanged=true`로 처리했다. 이어 발행된 Reservation Event ID `1ee3cd5a-e766-4663-aecc-eb8723dd9a54`를 Notification이 세 번째 시도에서 `notificationCreated=true`로 처리했다. Payment 이벤트 발행부터 최종 Notification 성공까지는 초 단위 발행 로그 기준 약 10.14초였다.

### 최종 정합성 및 DLT 확인

| 검증 항목 | 결과 |
|---|---:|
| Ticketing 처리 시도 | 3회 |
| Notification 처리 시도 | 3회 |
| Reservation 상태 | `CONFIRMED` |
| Reservation version | 1 |
| Payment 완료 시각 | 저장됨 |
| SeatHold 상태 | `CONFIRMED` |
| ScheduleSeat 상태 | `SOLD` |
| Reservation Inbox / 고유 이벤트 | 1건 / 1건 |
| Reservation Outbox / Published / 고유 이벤트 | 1건 / 1건 / 1건 |
| Notification / 고유 Source Event | 1건 / 1건 |
| Notification Inbox / 고유 이벤트 | 1건 / 1건 |
| `payment-events.DLT` | 10건 → 10건 |
| `reservation-events.DLT` | 10건 → 10건 |
| 최종 Ticketing Consumer Lag | 0 |
| 최종 Notification Consumer Lag | 0 |

### 보충 검증 판정

- 테스트 결과: **PASS**
- 두 Consumer 모두 합의한 2초 간격과 최초 포함 총 3회 처리 준수
- 일시적 오류가 해소된 세 번째 처리에서 상태 변경과 후속 이벤트 흐름 정상 완료
- 성공한 이벤트를 DLT로 보내지 않아 기존 DLT 10건 유지
- Inbox·Outbox·Notification 각 1건으로 멱등성과 최종 데이터 정합성 확인
- 최종 Consumer Lag 0으로 정상화 확인
