# CI 파이프라인

## 전체 흐름

`develop` 브랜치를 대상으로 PR을 생성하거나 커밋을 추가하면 [ci.yml](../.github/workflows/ci.yml)이 자동 실행됩니다.

```text
PR 생성 / 커밋 push
        │
        └── ci.yml
             ├─ 소스 코드 체크아웃
             ├─ JDK 21 설정
             ├─ Gradle 설정 및 캐시 복원
             └─ 서비스별 빌드·테스트
```

Gateway, Platform, Ticketing, Payment & Notification을 각각 독립된 작업으로 실행합니다.

## 트리거

| 조건 | 동작 |
| --- | --- |
| `develop` 대상 PR 생성·갱신 | 자동 실행 |
| `main` 대상 PR 또는 브랜치 push | 실행하지 않음 |

## 단계별 설명

**1. 소스 코드 및 실행 환경 준비**

PR의 코드를 체크아웃하고 JDK 21과 Gradle을 설정합니다. Gradle 캐시를 사용해 이전에 받은 의존성을 재사용합니다.

**2. 서비스별 빌드·테스트**

각 서비스에서 다음 명령을 실행합니다.

```bash
./gradlew :<서비스명>:build --no-daemon
```

컴파일과 JAR 생성, 일반 테스트 및 Docker 기반 통합 테스트를 함께 수행합니다. 어느 한 단계라도 실패하면 해당 서비스의 CI 작업이 실패합니다.

## 별도 통합 시나리오

전체 예매 흐름(S01)은 PR CI에서 자동 실행하지 않습니다. 필요할 때 [Happy Path 통합 테스트](../.github/workflows/integration-happy-path.yml)를 GitHub Actions에서 수동으로 실행합니다.

현재 CI에는 배포 단계가 없습니다.
