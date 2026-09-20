# S09 Queue 집중 부하

## 목적

Gateway를 경유한 Queue 진입·상태 조회·입장 허용·토큰 사용 흐름의 부하 특성과
순번·중복·입장 상한 정합성을 검증한다. Scheduler 조회 개선 전후 결과는
[로컬 결과](../../../docs/test-results/S09-queue-load/local-result.md)에서 확인한다.

## 준비

- Docker Desktop, Docker Compose, Python 3, JDK 21
- 저장소 루트의 개인 `.env`
- Platform DB의 기준 회차 `31000000-0000-0000-0000-000000000001`
- k6 이미지는 runner에 digest로 고정되어 있으며 Docker가 필요할 때 pull한다.

저장소 루트에서 공통 테스트 환경을 실행한다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --wait
docker compose -f docker-compose.yml -f docker-compose.test.yml ps
```

## 실행

기능·보안·토큰·heartbeat 사례:

```powershell
python -u scripts/test-scenarios/s09-queue-load/verify-s09.py
```

계획한 VU 단계, Spike, Soak:

```powershell
python -u scripts/test-scenarios/s09-queue-load/run-vu.py --mode vu
python -u scripts/test-scenarios/s09-queue-load/run-vu.py --mode spike
python -u scripts/test-scenarios/s09-queue-load/run-vu.py --mode soak
```

Polling 1·2·5초 비교:

```powershell
.\scripts\test-scenarios\s09-queue-load\run-polling-comparison.ps1
```

Queue Redis 회귀와 독립 JVM 2개 정합성·입장 상한 검증:

```powershell
.\gradlew.bat :ticketing-service:test --tests com.tikitaka.ticketing.queue.infrastructure.RedisQueueRepositoryTest --console=plain
.\gradlew.bat :ticketing-service:queueMultiInstanceVerify --console=plain
.\gradlew.bat :ticketing-service:queueMultiInstanceVerify --args=quota --console=plain
```

최종 소스 스냅샷으로 Ticketing 이미지를 빌드한 뒤 대표 VU만 회귀하려면 다음을 실행한다.

```powershell
.\scripts\test-scenarios\s09-queue-load\build-observed.ps1
python -u scripts/test-scenarios/s09-queue-load/run-vu.py --mode vu --vus 50 300 --restart none
```

## 확인

- k6 완료 사용자 수, 오류율, HTTP p95·p99와 입장 대기 p95·p99
- Redis Entry의 상태·sequence·토큰 참조 중복 또는 누락
- 회차별 모든 Scheduler 인스턴스를 합산한 고정 1초 구간 승인 수 최대 50명
- Grafana `queue-performance` 대시보드의 API·JVM·Queue·Scheduler 지표
- 부하 종료 뒤 WAITING·active와 CPU 회복

## PASS 기준

- 중복 Entry·입장 토큰·sequence가 없다.
- 완료 사용자 모두 정상 흐름을 마치고 Redis 정합성 검사가 통과한다.
- 회차별 합산 승인 수가 설정 상한을 초과하지 않는다.
- OOM, 반복 재시작, 지속적인 timeout·connection pool 고갈이 없다.
- 성능 목표가 합의되지 않은 지표는 수치만으로 PASS/FAIL을 판정하지 않는다.

## 정리

runner는 실행 회차의 Redis 키와 registry 항목을 정리하고 임시 JWT 파일을 삭제한다.
생성한 DB 사용자·회차·좌석은 자동 삭제하지 않는다. 전역 Redis 초기화나 볼륨 삭제를
정리 절차로 사용하지 않는다. 원본 실행 자료는 Git에서 제외된 `artifacts/`에 저장된다.

세부 버전 보존, 데이터 조건과 제한 사항은
[재현 문서](../../../docs/test-results/S09-queue-load/reproduction.md)를 참고한다.


## Queue API 직접 호출과 추가 진단

S09 보강 검증으로 수행한 등록 API 직접 호출 및 연결·의존성 진단 결과는
[직접 호출 결과](../../../docs/test-results/S09-queue-load/direct-result.md)에 정리했다.
기존 Gateway 경유 S09 기본 실행은 유지한다. 아래 명령은 저장소 루트의 PowerShell에서 실행한다.

### 준비 및 1,000 VU 직접 호출

공통 Compose 환경과 위 기준 회차가 필요하다. 실행 중인 Ticketing 환경을 보존하며 현재 소스를 빌드한다.
운영/공유 서비스 대상으로 실행하지 않는다. 직접 호출은 테스트 전용 신뢰 헤더를 사용한다.

```powershell
.\scripts\test-scenarios\s09-queue-load\build-observed.ps1
.\scripts\test-scenarios\s09-queue-load\run-ticketing-direct-spike.ps1 -PrepareMonitoring -ForceRecreate -AcceptCount 1000
Start-Sleep -Seconds 180
.\scripts\test-scenarios\s09-queue-load\run-ticketing-direct-spike.ps1 -AcceptCount 1000 -RequireStageMetrics -RequirePlatformDiagnostics
```

기본 등록 전용 1,000 VU·요청 5초이며 Platform은 실제 호출한다. 최초 dependency preflight 1건은 부하 집계 전 처리한다.
새 계정·회차·좌석을 준비하고 종료 시 해당 실행의 DB fixture와 Redis 키만 정리한다.
기존 journey 명령과 달리 직접 호출 wrapper에는 DB fixture 자동 정리가 켜져 있다.
등록만 측정하므로 admission journey·구매·자연 회복 성공으로 판단하지 않는다.

### 연결·의존성 추가 검증

```powershell
# 실제 Platform과 정상 stub 각 1회. 추가 빌드는 필요 없다.
.\scripts\test-scenarios\s09-queue-load\run-platform-comparison.ps1
# 각각 독립 실행. 사전 확인 1건을 제외한 부하 호출에만 주입한다.
.\scripts\test-scenarios\s09-queue-load\run-platform-comparison.ps1 -Fault delay500
.\scripts\test-scenarios\s09-queue-load\run-platform-comparison.ps1 -Fault error503
# 한 단계씩만 실행하고 실패 원인·회복을 확인하기 전 증량하지 않는다.
.\scripts\test-scenarios\s09-queue-load\run-registration-capacity.ps1 -Vus 2000
.\scripts\test-scenarios\s09-queue-load\run-registration-capacity.ps1 -Vus 10000
.\scripts\test-scenarios\s09-queue-load\run-platform-comparison.ps1 -Vus 10000
# 순간 Spike와 분리한 30초 분산 유입, 연결 한도 비교
.\scripts\test-scenarios\s09-queue-load\run-registration-capacity.ps1 -Vus 10000 -StartupSpreadSeconds 30
.\scripts\test-scenarios\s09-queue-load\run-registration-capacity.ps1 -Vus 10000 -StartupSpreadSeconds 30 -MaxConnections 16384
```

위 목록은 전체를 연달아 실행하라는 뜻이 아니다. 필요한 비교 하나를 선택한다.
`-MaxConnections` 비교는 원래 런타임 값을 확인하고 finally에서 그 값의 명시적 환경변수로 복구한다.
backlog 1,000 및 모니터링 설정은 로컬 비교 준비값으로 유지한다. 이를 운영 권장값으로 간주하지 않는다.
Platform 비교는 종료 시 실제 Platform 경로로 복구한 뒤 해당 stub 컨테이너만 제거한다.
stub은 `docker-compose.test.yml`의 opt-in `queue-dependency-test` profile이며 호스트 포트를 열지 않는다.
상태 fixture를 정확히 반환하지만 실제 Platform 인증·검증을 대체하지 않으며 Kafka 모델도 아니다.
503 주입은 예상된 k6 FAIL을 만든다. 비교 wrapper 종료 코드만 보지 말고 각 result.json을 확인한다.

### 관측과 실행 한계

- `result.json`: 클라이언트 성공·실패, HTTP/연결 지연, registrationElapsed, observationComplete.
- `tcp-delta.json`, `tomcat-samples.json`, `samples.json`: Ticketing·Platform TCP, thread/connection, Redis·컨테이너·k6 자원.
- `registration-breakdown.json`: 내부 단계별 count/sum/mean과 transport 오류 분류. 합계는 동시 요청들의 누적 시간이며 테스트 벽시계 시간이 아니다.
- stub 조건은 `platform-stub-fault.json`으로 식별. stub 실행의 TCP Platform 항목은 실제 Platform namespace이므로 stub TCP 결과로 해석하지 않는다.
- `collection-errors.json`은 부가 관측 실패다. 누락은 0이 아니며 관측 실패가 있어도 k6 결과는 보존한다.
- OOM·재시작·정합성 오류·지속 timeout이면 증량하지 않는다. k6와 서버가 같은 호스트를 공유하므로 부하 발생기 포화도 구분한다.
- 원본 JSON/로그/HTML/JFR 및 개인 환경 파일은 artifacts에만 보관한다. 결과 문서에 선별한 수치와 조건을 기록한다.

계측 기능은 공통 test Compose에서 `QUEUE_DIAGNOSTICS_REGISTRATION=true`,
`QUEUE_DIAGNOSTICS_PLATFORM_CLIENT=true`로 활성화한다. 애플리케이션 기본값은 비활성이다.
테스트 전용 임시 Compose override는 생성 후 정리되며 별도 시나리오 Compose 파일을 저장소에 추가하지 않는다.
