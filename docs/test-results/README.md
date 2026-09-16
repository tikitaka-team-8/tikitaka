# 통합 테스트 결과 안내

이 디렉터리는 통합 테스트 결과를 시나리오별로 확인할 수 있도록 정리한 **결과 문서 디렉터리**입니다.

- 실제 실행 스크립트와 Seed·검증 SQL은 [`scripts/test-scenarios`](../../scripts/test-scenarios)에서 관리합니다.
- 공통 테스트 환경 실행 방법과 새로운 시나리오를 추가하는 방법은 [통합 테스트 실행 가이드](../development/integration-test-guide.md)를 참고해 주세요.
- 이 디렉터리에는 실행 조건, 측정 결과, 판정 근거와 주요 문제 해결 과정을 기록해 주세요.

## 시나리오별 결과

| ID | 시나리오 | 주 담당 | Local 결과                                          | Staging 결과 |
|---|---|---|---------------------------------------------------|---|
| S01 | 전체 Happy Path | Infra·Gateway | [PASS · 완료](./S01-happy-path/local-result.md)     | 미실행 |
| S02 | 동일 좌석 동시 선점 | Seat·Seat Hold | 미작성                                               | 미실행 |
| S03 | 중복 결제 승인 | Payment | 미작성                                               | 미실행 |
| S04 | Seat Hold 만료·예매 생성 경합 | Seat Hold·Reservation | 미작성                                               | 미실행 |
| S05 | 응답 유실·재요청 | Reservation·Payment | 미작성                                               | 미실행 |
| S06 | 인증·인가·헤더 위조 | Gateway·Auth·User | 미작성                                               | 미실행 |
| S07 | 공연 변경 정합성 | Event·Session | 미작성                                               | 미실행 |
| S08 | Kafka·Consumer 장애 복구 | Reservation·Notification | [PASS · 완료](./S08-kafka-recovery/local-result.md) | 미실행 |
| S09 | Queue 집중 부하 | Queue | 미작성                                               | 미실행 |
| S10 | 전체 E2E 부하 | Infra | 미작성                                               | 미실행 |

## 결과 상태

| 표기 | 의미 |
|---|---|
| `미작성` | 결과 문서가 아직 작성되지 않았습니다. |
| `진행 중` | 테스트를 실행하거나 문제를 재현하고 있습니다. |
| `분석 중` | 재현한 문제의 원인과 개선 방법을 분석하고 있습니다. |
| `BLOCKED` | 외부 결정, 다른 도메인 작업 또는 환경 문제로 진행할 수 없습니다. |
| `PASS` | 정의한 기대 결과와 정합성 조건을 충족했습니다. |
| `FAIL` | 기대 결과 또는 정합성 조건을 충족하지 못했습니다. |
| `미실행` | 해당 환경에서는 아직 테스트를 실행하지 않았습니다. |

`진행 중`, `분석 중`, `BLOCKED`는 현재 작업 상태를 나타냅니다. 테스트가 완료되면 `PASS` 또는 `FAIL`과 그 판단 근거를 결과 문서에 기록해 주세요.

## 디렉터리 구성

```text
docs/test-results/
├─ README.md
├─ S01-happy-path/
│  └─ local-result.md
├─ S02-concurrent-seat-hold/
│  └─ local-result.md
└─ S{번호}-{시나리오명}/
   ├─ local-result.md
   └─ staging-result.md      # 스테이징 검증 대상으로 선정된 경우에만 생성
```

| 파일 | 역할 |
|---|---|
| `README.md` | 전체 시나리오의 진행 상태와 결과 문서 링크를 안내합니다. |
| `local-result.md` | 작업 브랜치의 로컬 통합환경에서 수행한 결과를 기록합니다. |
| `staging-result.md` | 선정된 대표 시나리오를 스테이징에서 수행한 결과를 기록합니다. |

아직 실행하지 않은 시나리오의 디렉터리와 결과 파일은 미리 만들지 않습니다.

## 결과 문서 확인 기준

각 결과 문서에는 다른 팀원이 실행 조건과 판단 근거를 확인할 수 있도록 다음 내용을 작성해 주세요.

- 실행 일시, 실행 환경, Branch와 Commit SHA
- 사용한 테스트 도구와 재현 자료
- 기대 결과와 실제 결과
- API·DB·Redis·Kafka 또는 서버 지표 중 시나리오 판정에 필요한 값
- `PASS` 또는 `FAIL` 판정과 근거
- 문제를 개선했다면 원인, 수정 내용과 동일 조건의 수정 전후 결과
- 남은 제한 사항 또는 후속 작업

실행 절차, 파일 배치 기준, 공통 판정 원칙과 작성 시 주의사항은 [통합 테스트 실행 가이드](../development/integration-test-guide.md)에서 확인해 주세요.
