# S08 Kafka·Consumer 장애 복구 로컬 테스트 결과

## 1. 결과 요약

| 항목 | 내용 |
|---|---|
| 시나리오 | S08 Kafka·Consumer 장애 복구 |
| 담당자 | 손유진 |
| 관련 도메인 | Payment, Reservation, Seat Hold, Schedule Seat, Notification |
| 테스트 유형 | 이벤트 장애·복구, API 부하, 데이터·이벤트 정합성 |
| 실행 환경 | Windows Local Docker Compose |
| 실행일 | 2026-09-15 |
| Branch | `fix/101-reservation-seat-payment-kafka-recovery` |
| 테스트 결과 | **PASS** |
| 진행 상태 | **완료** |
| 완료 대상 Issue | [#116 S08 Kafka Consumer 장애 복구 및 Retry·DLT 검증](https://github.com/tikitaka-team-8/tikitaka/issues/116) (`Closes #116`) |
| 관련 기능 Issue | [#101 좌석 선점 및 Payment·Kafka 처리 안정성 보강](https://github.com/tikitaka-team-8/tikitaka/issues/101) (`Related to #101`) |
| 관련 PR | PR 생성 후 링크 추가 |

Reservation·Notification Listener 중단 중 이벤트 보존, Consumer 복구, Retry·DLT 적용 전후 비교와 재시도 후 최종 성공을 검증했습니다. 정상 이벤트 1,000건은 Payment부터 Notification까지 중복·유실·상태 모순 없이 처리됐고 두 Consumer Group의 Lag은 0으로 정상화됐습니다.

이번 문서가 검증한 범위는 Reservation과 Notification **Consumer**의 Retry·DLT 및 최종 데이터 정합성입니다. Issue #101의 Reservation Outbox Producer 발행 재시도, Kafka 타임아웃, DLT 운영자 재처리, Payment 생성 응답 유실 복구는 별도 설계와 검증이 필요한 후속 범위로 분리했습니다.

### 개선 핵심 수치

> **Consumer별 실패 처리 시도는 100회에서 20회로 80.0% 감소했고, Notification의 실패 포함 처리 구간은 약 49.67초에서 23.78초로 52.1% 단축됐습니다. 정상 이벤트 1,000건의 성공률 100%와 최종 Lag 0을 유지하면서 최종 실패 10건을 DLT에 모두 격리했습니다.**

| 지표 | 개선 전 | 개선 후 | 결과 |
|---|---:|---:|---:|
| 실패 이벤트 | 10건 | 비재시도 5건 + 재시도 가능 5건 | 총 10건 유지 |
| Consumer별 총 처리 시도 | 100회 | 20회 | **80회, 80.0% 감소** |
| 이벤트당 처리 시도 | 모두 10회 | 비재시도 1회 / 재시도 가능 3회 | 불필요한 재시도 제거 |
| Notification 실패 포함 처리 구간 | 약 49.67초 | 약 23.78초 | **약 25.89초, 52.1% 단축** |
| 최종 실패 DLT 보존 | 0건 | Consumer별 10건 | **최종 실패 10/10건 격리** |
| 정상 이벤트 처리 | 1,000건 | 1,000건 | **성공률 100% 유지** |
| 최종 Consumer Lag | 0 | 0 | 정상화 유지 |
| Inbox·Outbox 중복 | 0건 | 0건 | 정합성 유지 |

개선의 목적은 모든 오류를 빠르게 넘기는 것이 아니라 오류 성격에 맞게 처리하는 것입니다. 복구 불가능한 오류는 즉시 DLT로 격리하고, 재시도 가능한 오류는 2초 간격으로 최초 포함 총 3회 처리합니다. 따라서 재시도 가능한 이벤트 한 건은 최종 실패까지 약 4초를 의도적으로 사용하지만, 모든 오류를 동일하게 반복하던 정책보다 전체 시도 수를 줄이고 실패 원본을 DLT에 보존합니다.

## 2. Issue·Commit·PR 추적

### 실행 버전과 결과 기록

| 구분 | 실행 또는 기준 Commit | 결과 기록 Commit | 결과 |
|---|---|---|---|
| 1차 정상 장애·복구 | 실행 SHA 별도 미기록 | [`1c2bf79`](https://github.com/tikitaka-team-8/tikitaka/commit/1c2bf794663e322e7df894fe408253fcd35b930e) | PASS, 예비 측정 |
| 2차 Retry·DLT 적용 전 Baseline | [`eaff507`](https://github.com/tikitaka-team-8/tikitaka/commit/eaff507f6b310f3aaffe8ddca6bf97541cd1aa55) | [`62a61be`](https://github.com/tikitaka-team-8/tikitaka/commit/62a61bea41290f33a3febe162cbedde436d9e7bb) | PASS, 문제 재현 |
| 3차 Retry·DLT 적용 후 비교 | [`81308ec`](https://github.com/tikitaka-team-8/tikitaka/commit/81308ec594ce8d69f91c520d30761db76ce573ea) | [`843b7ee`](https://github.com/tikitaka-team-8/tikitaka/commit/843b7eea461133acb231397f13158f260a2dc65e) | PASS, 개선 확인 |
| 재시도 후 성공 보충 검증 | [`4e369f6`](https://github.com/tikitaka-team-8/tikitaka/commit/4e369f6c3a9604f24b75c021026b9cef93366df2) | [`55baa6f`](https://github.com/tikitaka-team-8/tikitaka/commit/55baa6f931558c4562750c6b0a862a16b700a787) | PASS |

1차 실행 당시 Commit SHA는 별도로 기록하지 않아 결과 기록 Commit만 추적할 수 있습니다. 이후 실행부터는 테스트 대상 Commit과 결과 기록 Commit을 분리했습니다.

### 개선 코드와 검증 자료

| Commit | 변경 목적 |
|---|---|
| [`f786e56`](https://github.com/tikitaka-team-8/tikitaka/commit/f786e56e99349438861d07777dc181cdb1b91486) | Reservation·Notification Consumer Retry·DLT 구성 |
| [`15b1de4`](https://github.com/tikitaka-team-8/tikitaka/commit/15b1de438cd23da82f0b6c465f6d592ff01e27b7) | Retry 횟수, Backoff, 비재시도 예외와 DLT 자동 회귀 테스트 |
| [`2629be7`](https://github.com/tikitaka-team-8/tikitaka/commit/2629be7cf44d9e916d6328f0f79b35cd3b176399) | 비재시도·재시도 혼합 통합 시나리오와 실패 Fixture |
| [`81308ec`](https://github.com/tikitaka-team-8/tikitaka/commit/81308ec594ce8d69f91c520d30761db76ce573ea) | 실패 이벤트와 CSV Fixture의 사용자 매핑 수정 |
| [`4e369f6`](https://github.com/tikitaka-team-8/tikitaka/commit/4e369f6c3a9604f24b75c021026b9cef93366df2) | 두 번 실패 후 세 번째 성공하는 단일 이벤트 Fixture |
| [`6613f95`](https://github.com/tikitaka-team-8/tikitaka/commit/6613f95c072ed35b5cdf71094ea0306ba0061180) | S08 SQL을 테스트 단계별 경로로 정리 |
| [`3b355f3`](https://github.com/tikitaka-team-8/tikitaka/commit/3b355f3fcaa39b5cd9952c4ba4977ab015cc1f07) | S08 재현 가이드 작성 |



## 3. 재현 방법과 증거 자료

상세한 환경 준비, SQL 실행 순서, Kafka 주입, 검증 명령과 PASS 기준은 [S08 재현 가이드](../../../scripts/sql/s08-kafka-recovery/README.md)에 분리했습니다.

| 구분 | 경로 |
|---|---|
| 정상 장애·복구 k6 | [`scripts/k6/kafka-recovery.js`](../../../scripts/k6/kafka-recovery.js) |
| 실패 격리 k6 | [`scripts/k6/kafka-failure-recovery.js`](../../../scripts/k6/kafka-failure-recovery.js) |
| k6 CSV | [`scripts/k6/data/`](../../../scripts/k6/data/) |
| Kafka 실행·주입 | [`scripts/kafka/s08/`](../../../scripts/kafka/s08/) |
| 단계별 SQL과 실행 가이드 | [`scripts/sql/s08-kafka-recovery/`](../../../scripts/sql/s08-kafka-recovery/README.md) |

실행 결과는 k6 요약, SQL 검증 결과, Kafka UI의 Consumer Lag·Topic 메시지 수, Kafka Offset 조회, 서비스 로그와 Grafana를 교차 확인했습니다. Kafka UI·Grafana 화면과 원본 터미널 로그는 로컬 실행 중 확인했고 저장소에는 별도 원본 파일로 보존하지 않았으며, 판정에 사용한 시각과 수치는 아래에 전사했습니다.

### 공통 측정 환경

| 항목 | 값 |
|---|---|
| CPU | Intel Core i9-14900HX, 24 Core / 32 Logical Processor |
| 시스템 메모리 | 31.7 GB |
| Docker 할당 CPU / 메모리 | 32 / 15.5 GB |
| 서비스 인스턴스 | 서비스별 1개 |
| DB·Kafka 위치 | 서비스와 동일한 로컬 PC |
| Kafka / PostgreSQL / Grafana | 3.9.1 / 16.15 / 12.1.0 |
| 부하 도구 | k6 2.2.0, Windows Host 실행 |
| Docker Image Tag | 로컬 Compose `latest` |
| 적용 Profile | `docker` |

공식 비교는 같은 머신·Docker 자원·서비스 인스턴스 수, 정상 결제 1,000건, Consumer별 실패 이벤트 10건과 Topic별 단일 Partition을 유지했습니다. 개선 후에는 새 예외 분류 정책을 검증하기 위해 실패 10건을 비재시도 5건과 재시도 가능 5건으로 나눴습니다.

## 4. 1차 정상 장애·복구 예비 측정

### 입력과 API 결과

- 100 VU, VU별 10회, 정상 결제 승인 1,000건
- 각 VU 최초 요청을 0~990ms에 분산
- Reservation·Notification Listener `autoStartup=false`

| 지표 | 결과 |
|---|---:|
| 성공 | 1,000건, 100% |
| 실패율 | 0% |
| 처리량 | 267.07 req/s |
| 평균 / p95 | 301.75 ms / 773.35 ms |
| p99 | 미측정 |
| 최대 | 1.27 s |

### 복구와 정합성

| 구간 | 실제 처리 | 최종 Lag |
|---|---:|---:|
| Ticketing Payment Consumer | 1,000건, 약 9.31초 | 0 |
| Notification Reservation Consumer | 1,000건, 약 3.74초 | 0 |

Consumer 중단 중 Payment는 `APPROVED`, Payment Outbox는 `PUBLISHED` 1,000건이었고 Reservation·Seat·Notification은 처리 전 상태를 유지했습니다. 복구 후 Reservation `CONFIRMED`, Reservation Inbox·Outbox, SeatHold `CONFIRMED`, ScheduleSeat `SOLD`, Notification·Notification Inbox가 각각 1,000건이었으며 중복과 유실은 확인되지 않았습니다.

이 실행은 정상 이벤트 보존과 복구 흐름을 확인한 예비 측정입니다. p99, 정확한 장애 시작 시각과 실패 이벤트 영향은 측정하지 않아 2차 Baseline에서 보완했습니다.

## 5. 2차 Retry·DLT 적용 전 Baseline

### 목적과 입력

정상 결제 1,000건과 처리 불가능한 이벤트 10건을 함께 발행하여 기존 `DefaultErrorHandler`의 반복 처리, 파티션 지연, 후속 정상 이벤트 처리와 실패 보존 여부를 측정했습니다.

| 항목 | 값 |
|---|---|
| VU / iteration | 100 VU / VU별 6회 |
| 사용자 / 정상 승인 | 600명 / 1,000건 |
| Payment 실패 | `UNSUPPORTED_PAYMENT_EVENT` 10건 |
| Reservation 실패 | `UNSUPPORTED_RESERVATION_EVENT` 10건 |
| Consumer 시작 상태 | 두 Listener 중단 |

첫 시도는 Git Bash가 Kafka 컨테이너의 `/opt` 경로를 Windows 경로로 변환하여 Producer가 중단됐습니다. 정상 승인 1,000건은 워밍업으로 처리하고 Consumer Offset과 DB를 초기화한 뒤 공식 Baseline을 다시 실행했습니다.

### API 결과

| 지표 | 결과 |
|---|---:|
| 성공 | 1,000건, 100% |
| 실패율 | 0% |
| 처리량 | 640.48 req/s |
| 평균 / p95 / p99 | 92.29 / 204.88 / 275.48 ms |
| 최대 / 실행시간 | 393.54 ms / 약 1.6초 |

### Offset과 Consumer 처리

`payment-events` 공식 범위는 2,001~3,010이었습니다. 실패는 2,201~2,205와 2,406~2,410에 배치됐고 마지막 실패 뒤 정상 이벤트 600건이 존재했습니다.

| Consumer | 정상 처리 | 실패 처리 | 실제 결과 |
|---|---:|---:|---|
| Ticketing | 1,000건, 약 8.93초 | 실패 10건, 각 총 10회 | 최종 Lag 0, DLT 없음 |
| Notification | 1,000건, 약 3.52초 | 실패 10건, 약 49.67초 | 할당부터 Lag 0 약 53.42초, DLT 없음 |

기본 정책은 예외 유형과 관계없이 이벤트마다 최초 1회와 재시도 9회를 수행했습니다. Ticketing은 실패 구간 뒤 정상 처리를 재개했지만 실패 원본을 별도 보존하지 않았습니다. Notification 실패 이벤트는 정상 1,000건 뒤 Offset 3,011~3,020에 연속 배치되어 후속 정상 이벤트 대기 시간은 측정하지 못했으며, Lag은 `1,010 → 7 → 1 → 0`으로 감소했습니다.

최종적으로 Payment·Reservation·Seat·Notification 정상 데이터는 각 1,000건, 두 Consumer는 Member 1과 Lag 0, Inbox·Outbox 중복과 고아 데이터는 0건이었습니다.

### Baseline에서 확인한 문제

- 복구 불가능한 오류까지 이벤트당 총 10회 처리
- Consumer별 실패 10건에 총 100회 처리 시도
- Notification 파티션을 실패 처리에 약 49.67초 점유
- 최종 실패를 DLT에 격리하지 않아 운영자가 실패 원본을 분리 조회하기 어려움

테스트 결과는 **PASS**이며, 위 문제를 Retry·DLT 개선 대상으로 확정했습니다.

## 6. 3차 Retry·DLT 적용 후 비교

### 실행 조건

- 준비 확인: `2026-09-15T22:04:36.4902586+09:00`
- 부하 시작: `2026-09-15T22:09:58.8136213+09:00`
- 최종 확인 종료: `2026-09-15T22:24:40.3752153+09:00`
- Ticketing·Payment·Notification 이미지 재빌드 및 컨테이너 재생성
- 두 Consumer Member 0, Lag 0에서 시작
- Payment `READY`, Reservation `PAYMENT_PROCESSING`, SeatHold `RESERVED`, ScheduleSeat `HELD` 각 1,000건
- Inbox·Outbox·Notification 0건, 중복 조회 0행

비재시도 오류 5건은 즉시 DLT로 보내고, 재시도 가능 DB 오류 5건은 2초 간격으로 최초 포함 총 3회 처리한 뒤 DLT로 보내도록 구성했습니다.

### API와 Offset 결과

| 지표 | 결과 |
|---|---:|
| 정상 요청 / 성공률 | 1,000건 / 100% |
| HTTP 실패율 | 0% |
| 처리량 | 341.98 req/s |
| 평균 / p95 / p99 | 338.61 / 666.94 / 889.61 ms |
| 최대 | 1.29 s |

`payment-events` 신규 범위 4,021~5,030에서 실패는 4,021~4,025와 4,207~4,211에 분산됐습니다. 두 실패 구간 사이 정상 이벤트 181건, 마지막 실패 뒤 정상 이벤트 819건이 존재했습니다. `reservation-events` 신규 범위 4,031~5,040에서도 실패가 4,031~4,034, 4,087, 4,217~4,221에 분산됐고 마지막 실패 뒤 정상 이벤트 819건이 존재했습니다.

따라서 Baseline Notification 구간의 “실패 10건이 Topic 끝에 연속 배치”된 한계를 보완하고, 두 Consumer 모두 실패 뒤 후속 정상 Offset이 진행되는 것을 확인했습니다.

### Consumer 복구 결과

| 항목 | Ticketing | Notification |
|---|---:|---:|
| 복구 명령 시작 | 22:12:59.696 | 22:19:53.340 |
| 실제 처리 로그 구간 | 22:13:23.855~22:13:51.804 | 22:20:13.371~22:20:37.150 |
| 실제 Kafka 처리 | 약 27.95초 | 약 23.78초 |
| 정상 처리 | 1,000건 | 1,000건 |
| 비재시도 오류 | 5건 × 1회 | 5건 × 1회 |
| 재시도 가능 오류 | 5건 × 3회 | 5건 × 3회 |
| DLT | 10건 | 10건 |
| 최종 Lag | 0 | 0 |

복구 명령부터 완료까지 Ticketing 약 52.11초, Notification 약 43.81초였으나 각각 약 24초와 20초의 컨테이너 기동·Consumer Group 할당을 포함합니다. 정책 비교에는 실제 로그 구간을 사용했습니다. 재시도 가능 오류 로그는 Consumer별 15회였고 각 이벤트의 시도 간격은 약 2초였습니다.

### 최종 정합성과 자원

- Payment `APPROVED`와 Payment Outbox `PUBLISHED` 각 1,000건
- Reservation `CONFIRMED`, Reservation Inbox, Outbox `PUBLISHED` 각 1,000건
- SeatHold `CONFIRMED`, ScheduleSeat `SOLD` 각 1,000건
- Notification과 Notification Inbox 각 1,000건
- 이벤트·Payment·Reservation 식별자 중복 0건

| 관측값 | Ticketing | Payment·Notification |
|---|---:|---:|
| 복구 구간 CPU 최대 | 약 2.02% | 약 0.78% |
| 복구 구간 JVM Heap 최대 | 약 127.7 MiB | 약 92.4 MiB |

종료 후 `docker stats --no-stream`은 Ticketing 0.41% / 520.3 MiB, Payment·Notification 0.51% / 392.3 MiB, Kafka 1.19% / 1.047 GiB였습니다. 이는 부하 Peak가 아닌 안정 상태 참고값입니다.

테스트 결과는 **PASS**입니다. 총 처리 시도는 Consumer별 100회에서 20회로 감소했고, 복구 불가능한 오류의 즉시 격리, 재시도 가능 오류의 제한 처리, DLT 보존과 후속 정상 Offset 진행을 모두 확인했습니다. HTTP 처리량은 Baseline보다 낮았지만 두 실행 모두 Threshold를 충족했으며, 이번 개선의 판정 대상은 API 처리량이 아니라 실패 분류와 복구 가능성입니다.

## 7. 재시도 후 최종 성공 보충 검증

재시도 가능한 DB 오류가 첫 두 번 발생한 뒤 해소되면 세 번째 처리에서 정상 성공하는지 단일 이벤트로 검증했습니다. 1번 Reservation과 관련 Inbox·Outbox·Notification만 초기 상태로 되돌렸고 기존 DLT 10건은 유지했습니다.

| 항목 | 값 |
|---|---|
| 기록 시작 / 종료 | 22:44:25.517 / 22:48:29.558 |
| Payment Event ID | `f3000000-0000-0000-0000-000000000001` |
| Reservation ID | `81000000-0000-0000-0000-000000000001` |
| 실패 주입 | Consumer별 최초 2회 `SQLSTATE 40001`, 세 번째 허용 |

| Consumer | 1차 실패 | 2차 실패 | 3차 성공 | 최초 실패부터 성공 |
|---|---|---|---|---:|
| Ticketing | 22:45:51.308 | 22:45:53.317 | 22:45:55.328 | 약 4.020초 |
| Notification | 22:45:56.125 | 22:45:58.133 | 22:46:00.141 | 약 4.016초 |

Ticketing은 `statusChanged=true`, Notification은 `notificationCreated=true`로 세 번째 처리에 성공했습니다. Payment 발행부터 최종 Notification 성공까지는 초 단위 발행 로그 기준 약 10.14초였습니다.

| 검증 항목 | 결과 |
|---|---|
| Consumer별 시도 카운터 | 각 3회 |
| Reservation / SeatHold / ScheduleSeat | `CONFIRMED` / `CONFIRMED` / `SOLD` |
| Reservation Inbox / Outbox | 각 1건, Outbox `PUBLISHED` |
| Notification / Notification Inbox | 각 1건 |
| DLT 변화 | Payment 10 → 10, Reservation 10 → 10 |
| 최종 Lag | 두 Consumer 모두 0 |
| 중복 | 0건 |

테스트 결과는 **PASS**입니다. 2초 Backoff, 최초 포함 총 3회, 재시도 중 성공 시 DLT 미전송과 후속 도메인 흐름 완료를 확인했습니다.

## 8. 최종 판정과 남은 범위

| 완료 기준 | 판정 근거 |
|---|---|
| API와 최종 도메인 상태 일치 | 정상 승인 1,000건과 최종 상태 전부 일치 |
| 중복·유실·고아·상태 모순 없음 | SQL의 고유 이벤트·식별자와 상태 집계로 확인 |
| 실패 흐름 추적 가능 | Kafka Offset, Consumer Lag, Retry·DLT 로그와 Grafana로 확인 |
| 성능 지표와 병목 기록 | 처리량, 오류율, 평균, p95·p99와 실패 파티션 점유 기록 |
| 장애 복구 시간과 정합성 확인 | Consumer별 실제 로그 구간, Lag 0과 최종 SQL 확인 |
| 문서와 스크립트로 재현 가능 | 단계별 [S08 재현 가이드](../../../scripts/sql/s08-kafka-recovery/README.md) 작성 |
| 개선 전후와 Issue 연결 | Baseline·개선 Commit, Issue #116·#101 연결 |

S08 Consumer Retry·DLT 테스트는 **PASS**, 진행 상태는 **완료**입니다. 다음 항목은 Issue #101에서 이어 진행할 후속 범위입니다.

- Reservation Outbox Producer 발행 실패 재시도
- DLT 이벤트 조회 후 안전한 재처리 기능
- Kafka 처리 타임아웃 적용
- Payment 생성 응답 유실 복구와 중복 결제 방지

