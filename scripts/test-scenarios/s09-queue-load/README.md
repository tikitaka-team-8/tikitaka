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
