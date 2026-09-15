# S09 실행 방법

## 개선 전후 버전 고정

최종 변경은 PR 하나로 관리하며 비교 버전은 다음 태그로 별도 보존한다.

| 태그 | 목적 | 출처 |
|---|---|---|
| `queue-perf-before-reconstructed` | 재실행용 순차 조회 비교 기준 | After snapshot에서 pipeline 변경만 제거해 재구성. 과거 Before 소스 체크섬과 일치 확인 안 됨 |
| `queue-perf-after-snapshot` | pipeline 후, quota 전 | 보존 snapshot. 측정 시 기록한 Scheduler/Service/Repository/application.yaml/baseline runner 5개 체크섬 일치 |
| `queue-perf-final-quota` | quota 적용 후 최종 코드 | 최종 회귀에 사용한 코드가 포함된 제출 commit `800f0ea` |

태그는 develop 병합이나 작업 브랜치 삭제와 독립적으로 특정 commit을 가리킨다.
원격 저장소에서 사용할 때는 세 태그도 명시적으로 push해야 한다. 비교 태그에 대한 별도 PR은 필요 없다.
과거 Before 4회 결과를 재구성한 Before commit에서 실행한 결과로 표기하지 않는다.
과거 결과는 원래 기록을 유지하고, 재구성 버전으로 새 실험을 하면 새 실행 ID로 전후를 함께 측정한다.
After도 기록된 5개 파일 외 모든 환경의 동일성이 증명된 것은 아니다. 빌드 보조 파일은 기준 commit과 보존 snapshot을 사용했다.

```powershell
# 원격 태그가 보존된 뒤, 저장소 루트에서 비교용 별도 폴더 생성
git worktree add --detach ..\queue-compare-before queue-perf-before-reconstructed
git worktree add --detach ..\queue-compare-after queue-perf-after-snapshot
git diff --ignore-space-at-eol queue-perf-before-reconstructed queue-perf-after-snapshot -- ticketing-service/src/main/java/com/tikitaka/ticketing/queue/infrastructure/RedisQueueRepository.java
```

두 비교 폴더에서는 동일 JDK 21/Docker/Redis 조건으로
`.\gradlew.bat :ticketing-service:queueSchedulerBaseline --console=plain`을 각각 실행할 수 있다.
비교 태그는 quota 이전 코드이며, 현재 최종 작업 브랜치에서 무대기 baseline을 실행하는 것과 다르다.
이번 버전 보존 작업에서는 성능 테스트를 실행하지 않았다. 상세 commit 및 출처는 [버전 근거](evidence/versions.json)를 따른다.

이 문서는 재현용 명령이다. 이미 완료한 모든 실험을 제출 전에 다시 실행하라는 뜻은 아니다.
저장소 루트 Windows PowerShell에서 실행한다. 기존 runner는 로컬 전용이며 스테이징에 그대로 사용하지 않는다.

## 선행 조건과 데이터

- Docker Desktop Linux, Docker Compose, Python 3, JDK 21. Gradle은 저장소 wrapper 사용.
- Compose Gateway(8000), Platform(8081), Ticketing(8082), 각 PostgreSQL/Redis, Kafka 및 Prometheus(9090)가 정상 실행 중이어야 한다. Grafana는 관측용이다.
- DB 접속 정보는 각 볼륨 최초 생성 시 설정과 일치해야 한다. Platform/Ticketing Service Key와 Gateway/Platform JWT 설정도 일치시킨다. 비밀값은 개인 `.env`로 준비한다.
- Platform DB에 원본 회차 `31000000-0000-0000-0000-000000000001` 필요. [Platform seed](../../../scripts/integration-test/seed/platform-seed.sql), [Ticketing seed](../../../scripts/integration-test/seed/ticketing-seed.sql)를 확인한다. 빈 환경에서 seed부터 재현하는 절차는 이번 최종 회귀에서 재검증하지 않았으며 공유 DB에 무조건 재적용하지 않는다.
- 컨테이너 이름은 기존 Compose의 `tikitaka-*`를 사용한다. 전체 Compose 기동은 kafka-ui 이미지 접근 문제 이력이 있어 테스트에 불필요한 UI까지 강제로 기동하지 않는다.

## 코드와 역할

| 파일 | 역할 |
|---|---|
| [run-vu.py](../../../scripts/k6/run-vu.py) | 준비·health 확인·k6 실행·Redis 정합성·회복·정리·증거 저장 |
| [queue-vu.js](../../../scripts/k6/queue-vu.js) | 실제 HTTP 부하와 사용자 흐름/checks. Python이 부하 도구를 대신하는 구조가 아님 |
| [verify-s09.py](../../../scripts/k6/verify-s09.py) | 인증/부정 토큰/중복/heartbeat/이탈 기능 사례 |
| [run-polling-comparison.ps1](../../../scripts/k6/run-polling-comparison.ps1) | 동일 300 VU에서 polling 1/2/5초 비교 |
| [build-observed.ps1](../../../scripts/performance/build-observed.ps1) | 실제 소스 snapshot/hash 생성, Ticketing 이미지 빌드·적용 |
| [QueueMultiInstanceVerification.java](../../../ticketing-service/src/test/java/com/tikitaka/ticketing/queue/performance/QueueMultiInstanceVerification.java) | 임시 Redis + 독립 Scheduler JVM 2개. 기본 중복 경합/quota 모드 |

세부 기대값과 과거 Scheduler 실험 조건은 [결과 보고서](local-result.md)에 포함했다. 기능 smoke는 verify-s09.py가 queue-load.js를 실행한다.

## 최종 코드 대표 회귀

JAVA_HOME은 각 PC의 JDK 21 설치 경로를 사용한다.

```powershell
& {
    $ErrorActionPreference = 'Stop'
    .\scripts\performance\build-observed.ps1
    if ($LASTEXITCODE -ne 0) { throw '빌드·적용 실패' }
    python -u scripts/k6/run-vu.py --mode vu --vus 50 300 --restart none
    if ($LASTEXITCODE -ne 0) { throw '회귀 실패: 결과부터 확인' }
}
```

각 120초 동안 constant VU, polling 2초, heartbeat 15초, 흐름 완료 후 think time 0.2초.
앱 재시작은 빌드 적용으로 인한 Ticketing 교체만 하며 runner의 별도 재시작은 없다.
처리량/지연 목표는 미확정. 기능 오류, 정합성/회복 실패 시 다음 단계로 올리지 않는다.

## 기타 실행 모드

각 명령은 독립 실험이다. 필요한 사례만 선택한다.

```powershell
# 기능/보안
python -u scripts/k6/verify-s09.py
# 전체 VU 단계: 50/100/300/500/1000, 각 120초
python -u scripts/k6/run-vu.py --mode vu
# 1000명의 단회 진입
python -u scripts/k6/run-vu.py --mode spike
# 300 VU 10분
python -u scripts/k6/run-vu.py --mode soak
# polling 비교
.\scripts\k6\run-polling-comparison.ps1
# Redis 회귀 및 합산 상한 (각각 종료 코드 확인)
.\gradlew.bat :ticketing-service:test --tests com.tikitaka.ticketing.queue.infrastructure.RedisQueueRepositoryTest --console=plain
.\gradlew.bat :ticketing-service:queueMultiInstanceVerify --args=quota --console=plain
# 기존 중복 승인 경쟁 모드
.\gradlew.bat :ticketing-service:queueMultiInstanceVerify --console=plain
```

기동 직후 실패 재현은 별도 `--mode spike --restart apps` 조건이다. 위 정상 상태 실험에 섞지 않는다.
과거 무대기 Scheduler baseline은 quota 도입 이전 소스의 측정이다. 현재 코드에서 같은 명령을 실행하면 정책에 의해 거절되므로 과거 비교용으로 실행하지 않는다.

## 결과 확인과 초기화

`Results:` 경로의 environment.json은 branch/HEAD·이미지·부하 설정,
각 VU 폴더 fixture.json은 회차/좌석 ID, result.json과 k6-summary.json은 수치,
integrity.json은 상태/sequence/토큰 관계, recovery.json은 회복 gate,
cleanup.json은 회차별 Redis 정리 여부를 기록한다.
samples.json/Prometheus 자료로 CPU·heap·admission·WAITING·Scheduler 시간과 Redis PING/INFO를 대조한다.
HTTP RPS와 사용자 흐름 완료율은 다르며 대기시간에는 polling 감지 지연이 포함된다.

runner는 새 회차를 만들어 이전 회차 부하와 분리하고, 회복 확인 뒤 그 회차 Redis 키와 registry 항목만 정리한다.
임시 JWT 파일은 제거한다. DB 사용자·회차·좌석 fixture는 보존하므로 DB까지 초기화됐다고 기록하지 않는다.
DB 정리가 필요하면 해당 실행 users.json/fixture.json ID만 식별하고 FK 관계를 확인해 삭제한다.
전역 FLUSHALL이나 볼륨 삭제를 초기화 절차로 사용하지 않는다. 자동 DB cleanup은 현재 제공되지 않는다.

두 JVM runner는 별도 임시 Redis를 만들고 종료 시 제거한다. 기존 서비스 Redis에는 접근하지 않는다.
quota.json의 FAIL은 확정 성공 수가 50 초과, INCONCLUSIVE는 경계/시각 자료 불확실이다.
실패는 반복 실행 전에 failure/worker 로그를 확인한다.

큰 원본 artifacts는 로컬에 보존된다. PR에는 작은 요약과 출처·체크섬을 선별한다.
공유용 계정의 비밀값이나 JWT를 결과에 넣지 않는다. 새 팀원 PC에서의 처음부터 재현 확인은 아직 남아 있다.


## 관측 설정과 제출 범위

추가 관측 설정은 `infra/compose.baseline.yml`, `infra/prometheus/prometheus.yml`,
팀 공통 `infra/grafana/provisioning/dashboards/dashboards.yml`과 `json/ticketing-baseline.json`을 사용한다.
기존 docker-compose.yml와 결합하는 override이며, 기존 환경을 덮어쓰기 전에 현재 설정을 확인한다.
Dashboard 지표는 run-vu.py의 QUERIES와 함께 읽는다. process_cpu_usage와 Docker CPU%는 분모가 다르다.

과거 Scheduler 5,000명 실험은 test-classpath standalone JVM, 전용 Redis, Clock.fixed,
fixedDelay 없이 cycle 연속 호출, seed와 최종 검증 제외 조건이었다. 현재 quota 도입 이후에는 같은 비교가 불가능하다.
QueueSchedulerBaseline.java는 이력용 코드로 보존하며 기존 전후 결과를 현재 실행으로 대체하지 않는다.

DB fixture 삭제 자동화와 빈 PC 최초 seed 실행은 검증 완료가 아니다. 스테이징은 develop 반영과 팀 전체 로컬 안정화 후 대표 사례를 축소하여 수행한다.
