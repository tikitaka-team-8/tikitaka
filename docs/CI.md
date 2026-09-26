# CI 파이프라인

## 개요

TIKITAKA의 CI는 `develop` 브랜치에 병합될 코드가 네 서비스에서 정상적으로 빌드되고 테스트를 통과하는지 검증합니다.

`develop` 대상 Pull Request를 생성하거나 새 commit을 추가하면 [ci.yml](../.github/workflows/ci.yml)이 자동 실행됩니다.

```text
develop 대상 Pull Request
        │
        └─ Service Build and Test
             ├─ Gateway 빌드·테스트
             ├─ Platform 빌드·테스트
             ├─ Ticketing 빌드·테스트
             └─ Payment & Notification 빌드·테스트
```

네 서비스는 GitHub Actions Matrix를 사용해 서로 독립된 작업으로 실행됩니다. 한 서비스가 실패해도 다른 서비스 작업을 즉시 취소하지 않으므로 서비스별 결과를 함께 확인할 수 있습니다.

---

## 트리거

| 조건 | 동작 |
| --- | --- |
| `develop` 대상 PR 생성·갱신 | 자동 실행 |
| `main` 대상 PR | 실행하지 않음 |
| 브랜치 push | 실행하지 않음 |

같은 PR에 새 commit이 추가되면 이전 CI 실행을 취소하고 최신 코드 기준으로 다시 검증합니다. 불필요한 중복 실행을 줄이고 가장 최근 결과를 PR에 표시하기 위한 설정입니다.

CI 워크플로우는 저장소 내용을 읽는 권한만 사용하며 AWS 자격 증명을 발급받지 않습니다.

---

## 실행 환경

각 서비스 작업은 다음 환경을 동일하게 사용합니다.

| 항목 | 설정 |
| --- | --- |
| 실행 환경 | GitHub Actions Ubuntu Runner |
| Java | Temurin JDK 21 |
| 빌드 도구 | Gradle Wrapper |
| 캐시 | Gradle 의존성과 빌드 캐시 |
| 작업 제한 시간 | 서비스별 10분 |

Gradle 캐시는 이전 실행에서 받은 의존성과 빌드 데이터를 재사용해 반복 실행 시간을 줄입니다.

---

## 서비스별 빌드와 테스트

각 Matrix 작업은 담당 서비스에서 다음 명령을 실행합니다.

```bash
./gradlew :<서비스명>:build --no-daemon
```

`build`는 컴파일과 JAR 생성을 수행하고 `check`를 통해 일반 테스트와 통합 테스트를 모두 실행합니다.

```text
build
 ├─ 컴파일
 ├─ test
 │    └─ integration 태그가 없는 일반 테스트
 ├─ integrationTest
 │    └─ integration 태그가 있는 테스트
 └─ 실행 가능한 JAR 생성
```

PostgreSQL이나 Redis처럼 외부 테스트 인프라가 필요한 테스트에는 JUnit `integration` 태그를 사용합니다. 해당 테스트는 Testcontainers로 필요한 컨테이너를 준비하며 `integrationTest` 작업에서 실행됩니다.

컴파일, 일반 테스트, 통합 테스트 또는 JAR 생성 중 하나라도 실패하면 해당 서비스의 CI 작업이 실패합니다. PR을 병합하기 전에 네 서비스의 작업 결과를 모두 확인해야 합니다.

---

## Happy Path 통합 테스트

PR CI는 서비스별 빌드와 테스트에 집중합니다. Gateway부터 데이터베이스와 Kafka까지 전체 환경을 연결하는 S01 Happy Path는 [integration-happy-path.yml](../.github/workflows/integration-happy-path.yml)에서 필요할 때 수동으로 실행합니다.

```text
Docker Compose 환경 기동
        ↓
테스트 데이터 준비
        ↓
회원가입·로그인
        ↓
공연 조회·대기열·좌석 선점
        ↓
예매 생성·결제 승인
        ↓
예매 및 좌석 확정
        ↓
알림 조회·읽음
        ↓
서비스별 DB 최종 상태 검증
```

Happy Path는 실행할 때마다 JWT Secret과 내부 서비스 키를 임시로 생성합니다. 실패하면 애플리케이션·데이터 컨테이너 로그와 Outbox·Inbox 상태를 GitHub Actions Artifact로 7일간 보존합니다. 실행이 끝나면 성공 여부와 관계없이 테스트 컨테이너와 볼륨을 정리합니다.

---

## CI와 CD의 관계

CI는 Pull Request의 코드 품질을 검증하며 배포하지 않습니다. PR이 `develop`에 병합된 뒤에는 별도의 CD 워크플로우가 스테이징 환경을 배포합니다.

```text
Pull Request
   ↓
CI 빌드·테스트
   ↓
develop 병합
   ↓
CD 스테이징 자동 배포
```

배포 트리거, ECR 이미지 관리와 ECS·Data EC2 배포 방식은 [CD 파이프라인](./CD.md)에서 확인할 수 있습니다.

---

## 관련 파일

| 파일 | 역할 |
| --- | --- |
| [.github/workflows/ci.yml](../.github/workflows/ci.yml) | PR 서비스별 빌드·테스트 |
| [build.gradle](../build.gradle) | 일반 테스트와 통합 테스트 작업 정의 |
| [.github/workflows/integration-happy-path.yml](../.github/workflows/integration-happy-path.yml) | 수동 S01 Happy Path 실행 |
| [scripts/test-scenarios/s01-happy-path](../scripts/test-scenarios/s01-happy-path) | Happy Path 실행·검증 스크립트 |
| [docs/CD.md](./CD.md) | 스테이징 배포 파이프라인 |
