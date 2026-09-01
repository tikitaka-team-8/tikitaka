# TIKITAKA 로컬 개발 환경 설정 가이드
작성: 2026-08-31
수정 버전: v1(2026-09-01)


이 문서는 TIKITAKA 프로젝트의 현재 로컬 개발 환경을 구성하기 위한 상세 가이드입니다. 평소에는 공통 인프라만 Docker Compose로 실행하고, 개발할 Spring Boot 애플리케이션은 IntelliJ에서 `local` 프로파일로 실행합니다.

## 1. 빠른 시작

Git, JDK 21, Docker Desktop과 IntelliJ EnvFile 플러그인이 준비되어 있다면 다음 순서로 실행합니다.

```cmd
git clone https://github.com/tikitaka-team-8/tikitaka.git
cd tikitaka
git switch develop
docker compose config --quiet
docker compose up -d --wait --wait-timeout 120
.\gradlew test --no-daemon
```

명령 실행 전에 따로 공유된 `.env`를 저장소 루트에 저장해야 합니다. 그다음 IntelliJ Run Configuration에서 EnvFile로 `.env`를 주입하고 `local` 프로파일을 지정합니다.

## 2. 필요한 프로그램

| 도구 | 기준 | 확인 명령 |
| --- | --- | --- |
| Git | 최신 안정 버전 권장 | `git --version` |
| JDK | Java 21 | `java -version` |
| Docker Desktop | Docker Compose v2 포함 | `docker compose version` |
| IntelliJ IDEA | Community 또는 Ultimate | IDE에서 확인 |
| EnvFile | IntelliJ 환경변수 파일 주입 플러그인 | IntelliJ Plugins에서 확인 |

IntelliJ의 Project SDK와 Gradle JVM도 모두 Java 21로 설정합니다.

- Project SDK: `File > Project Structure > Project SDK`
- Gradle JVM: `Settings > Build, Execution, Deployment > Build Tools > Gradle`

## 3. 저장소와 작업 브랜치 준비

```cmd
git clone https://github.com/tikitaka-team-8/tikitaka.git
cd tikitaka
git switch develop
git pull origin develop
git switch -c feat/이슈번호-작업명
```

브랜치는 최신 `develop`에서 만듭니다. 예시는 `feat/12-user-signup`입니다. `main`과 `develop`에는 직접 Push하지 않고 Pull Request를 통해 병합합니다.

브랜치 타입은 작업 성격에 맞게 `feat`, `fix`, `refactor`, `test`, `docs`, `chore` 중에서 선택합니다.

## 4. 팀 로컬 환경변수 준비

### 실제 `.env` 받기

공유된 실제 로컬 개발용 `.env`를 받아 저장소 루트에 저장합니다.

```text
tikitaka/
├─ .env
├─ .env.example
├─ docker-compose.yml
└─ settings.gradle
```

파일명이 `.env.txt`가 되지 않도록 확인합니다.

### `.env.example`의 역할

- `.env.example`은 저장소를 보는 사람이 필요한 환경변수 이름과 형식을 확인하는 용도의 예시 파일입니다.
- 실제 팀 secret 값인 `.env`는 Git에 포함하지 않습니다.
- 팀 전체에 필요한 변수가 추가되면 실제 값이 아닌 예시를 `.env.example`에도 추가합니다.

팀 공유 파일을 받을 수 없는 경우에는 `.env.example`을 복사해 독립된 로컬 환경을 구성할 수 있습니다. 이 경우 팀 공유 DB 계정과 다른 값이 만들어질 수 있습니다.

Windows Command Prompt:

```cmd
copy .env.example .env
```

macOS 또는 Linux:

```bash
cp .env.example .env
```

### `.env` 설정 관련 참고 사항

- Docker Compose는 저장소 루트의 `.env`를 자동으로 읽습니다.
- Spring Boot와 IntelliJ는 루트 `.env`를 자동으로 읽지 않습니다.
- IntelliJ에서 실행하는 애플리케이션에는 EnvFile 플러그인으로 `.env`를 주입합니다.

## 5. 공통 인프라 실행

먼저 Compose 설정 문법과 환경변수를 확인합니다.

```cmd
docker compose config --quiet
```

오류가 없으면 인프라를 실행합니다.

```cmd
docker compose up -d --wait --wait-timeout 120
docker compose ps
```

모든 항목이 `running` 또는 `healthy`이면 준비가 끝난 것입니다.

평소 도메인 개발에서는 전체 인프라를 모두 실행하지 않고 담당 서비스에 필요한 항목만 실행해도 됩니다.

```cmd
# Platform Service
docker compose up -d --wait platform-postgres platform-redis kafka

# Ticketing Service
docker compose up -d --wait ticketing-postgres ticketing-redis kafka

# Payment & Notification Service
docker compose up -d --wait payment-postgres kafka
```

Gateway는 현재 DB, Redis와 Kafka를 사용하지 않으므로 기본 기능 개발에는 별도 인프라가 필요하지 않습니다. Kafka UI는 메시지 확인이 필요할 때, Prometheus와 Grafana는 메트릭 확인이 필요할 때만 추가로 실행합니다. 전체 서비스 연동이나 초기 환경 점검에서는 기존 전체 실행 명령을 사용하시면 됩니다.

| 구성 요소 | Host 주소 | 사용 서비스 |
| --- | --- | --- |
| Platform PostgreSQL | `localhost:5433` | Platform Service |
| Ticketing PostgreSQL | `localhost:5434` | Ticketing Service |
| Payment PostgreSQL | `localhost:5435` | Payment & Notification Service |
| Platform Redis | `localhost:6380` | Platform Service |
| Ticketing Redis | `localhost:6381` | Ticketing Service |
| Kafka | `localhost:9092` | 세 도메인 서비스 |
| Kafka UI | <http://localhost:8085> | 토픽과 메시지 확인 |
| Prometheus | <http://localhost:9090> | 메트릭 확인 |
| Grafana | <http://localhost:3000> | 대시보드 확인 |

각 서비스는 자신의 PostgreSQL과 Redis만 사용합니다. Payment & Notification Service에는 Redis가 없습니다.

## 6. IntelliJ에서 애플리케이션 실행

1. 저장소 루트를 Gradle 프로젝트로 엽니다.
2. Gradle 동기화가 끝날 때까지 기다립니다.
3. Project SDK와 Gradle JVM이 Java 21인지 확인합니다.
4. `Settings > Plugins > Marketplace`에서 `EnvFile`을 설치합니다.
5. 실행할 모듈의 Spring Boot 메인 클래스를 찾습니다.
6. `Run > Edit Configurations`에서 해당 애플리케이션의 Run Configuration을 엽니다.
7. EnvFile 사용을 활성화하고 저장소 루트의 `.env`를 선택합니다.
8. Active profiles에 `local`을 입력합니다.
9. Docker Compose 인프라가 준비된 상태에서 실행합니다.

Active profiles 입력란이 없다면 Program arguments에 다음 값을 넣는 방법도 있습니다.

```text
--spring.profiles.active=local
```

환경변수로도 지정할 수 있습니다.

```text
SPRING_PROFILES_ACTIVE=local
```

| 모듈 | 포트 | 연결 대상 |
| --- | ---: | --- |
| Gateway | 8000 | DB, Redis, Kafka 없음 |
| Platform Service | 8081 | Platform PostgreSQL, Platform Redis, Kafka |
| Ticketing Service | 8082 | Ticketing PostgreSQL, Ticketing Redis, Kafka |
| Payment & Notification Service | 8083 | Payment PostgreSQL, Kafka |

Platform, Ticketing, Payment & Notification의 Run Configuration에는 각각 EnvFile 설정이 필요합니다. Gateway는 현재 DB, Redis와 Kafka를 사용하지 않지만 실행 방식의 일관성을 위해 같은 `.env`를 선택해도 문제없습니다.

현재 Docker Compose에는 Spring Boot 애플리케이션이 등록되어 있지 않습니다. 다른 서비스와 함께 수동 테스트하려면 필요한 애플리케이션을 IntelliJ에서 각각 실행해야 합니다. 다른 팀원의 애플리케이션을 컨테이너로 실행하는 구성은 Dockerfile과 애플리케이션 Compose 작업 이후 제공할 예정입니다.

## 7. `local`과 `docker` 프로파일 차이

- `local`: IntelliJ 또는 Host에서 애플리케이션을 실행할 때 사용합니다.
- `docker`: 향후 애플리케이션을 Docker Compose 내부에서 실행할 때 사용합니다.

| 자원 | `local` | `docker` |
| --- | --- | --- |
| Platform PostgreSQL | `localhost:5433` | `platform-postgres:5432` |
| Ticketing PostgreSQL | `localhost:5434` | `ticketing-postgres:5432` |
| Payment PostgreSQL | `localhost:5435` | `payment-postgres:5432` |
| Platform Redis | `localhost:6380` | `platform-redis:6379` |
| Ticketing Redis | `localhost:6381` | `ticketing-redis:6379` |
| Kafka | `localhost:9092` | `kafka:19092` |

컨테이너 안에서 `localhost`는 해당 컨테이너 자신을 의미합니다. Docker 내부 통신에는 반드시 Compose 서비스명과 컨테이너 포트를 사용합니다.

## 8. 테스트와 기본 확인

Windows:

```cmd
.\gradlew test --no-daemon
```

macOS 또는 Linux:

```bash
./gradlew test --no-daemon
```

현재 CI와 같은 명령으로 전체 모듈을 컴파일하고 테스트를 실행합니다. 데이터 서비스의 DB 통합 테스트는 `test` 프로파일과 PostgreSQL Testcontainers를 사용하므로 로컬 Compose, `.env`, `local` 또는 `docker` 프로파일에 의존하지 않습니다. 테스트 실행 전 Docker Desktop이 실행 중이어야 합니다.

### 테스트 작성 규칙

- 단위 테스트는 JUnit과 필요한 경우 Mockito를 사용하며 Spring Context와 외부 인프라를 실행하지 않습니다.
- 전체 Spring Context와 PostgreSQL이 필요한 통합 테스트는 서비스별 `@PostgresIntegrationTest`를 사용합니다.
- Repository slice 테스트는 서비스별 PostgreSQL Testcontainers 설정을 명시적으로 가져와 실제 PostgreSQL에서 검증합니다.
- 테스트에서 `local` 또는 `docker` 프로파일을 직접 활성화하지 않습니다.
- JDBC URL, DB 계정, 비밀번호와 고정 포트를 테스트 코드나 `application-test.yaml`에 작성하지 않습니다.
- Redis와 Kafka 테스트 인프라는 실제 연동 동작을 검증해야 할 때 서비스별 공통 설정으로 추가합니다.
- Pull Request를 열기 전에 `.\gradlew test --no-daemon`을 실행합니다.

서비스가 실행되면 Health 주소를 확인합니다.

- Gateway: <http://localhost:8000/actuator/health>
- Platform: <http://localhost:8081/actuator/health>
- Ticketing: <http://localhost:8082/actuator/health>
- Payment & Notification: <http://localhost:8083/actuator/health>

테스트 PostgreSQL에서는 서비스별 Flyway 마이그레이션을 적용하고 Hibernate `ddl-auto: validate`로 Entity와 스키마의 일치 여부를 검증합니다. 현재 Redis와 Kafka Testcontainers는 사용하지 않습니다.

## 9. 인프라 종료와 데이터 초기화

컨테이너만 종료하고 데이터는 보존합니다.

```powershell
docker compose down
```

다음 명령은 PostgreSQL, Redis, Kafka, Prometheus와 Grafana의 로컬 볼륨을 삭제합니다. 필요한 데이터가 없고 명확한 초기화 목적이 있을 때만 실행합니다.

```powershell
docker compose down -v
```

## 10. 자주 발생하는 문제

### 포트가 이미 사용 중인 경우

오류 메시지에 표시된 포트를 사용하는 기존 프로그램이나 컨테이너를 종료합니다. 합의된 포트를 개인 판단으로 변경하면 팀원과 실행 결과가 달라지므로 먼저 공유합니다.

### 데이터베이스 연결이 실패하는 경우

1. `docker compose ps`에서 해당 PostgreSQL이 `healthy`인지 확인합니다.
2. IntelliJ 실행 프로파일이 `local`인지 확인합니다.
3. IDE 실행에서 `platform-postgres` 같은 Docker 내부 주소를 사용하지 않았는지 확인합니다.

### Docker Compose가 환경변수를 찾지 못하는 경우

저장소 루트에 `.env`가 있는지 확인합니다. Windows에서는 파일이 `.env.txt`로 저장되지 않았는지도 확인합니다.

### IntelliJ 애플리케이션이 환경변수를 찾지 못하는 경우

1. EnvFile 플러그인이 설치되고 활성화됐는지 확인합니다.
2. 현재 실행한 Run Configuration에 EnvFile이 활성화됐는지 확인합니다.
3. 저장소 루트의 `.env`가 선택됐는지 확인합니다.
4. Run Configuration을 수정한 뒤 애플리케이션을 완전히 재시작합니다.
5. `local` 프로파일이 활성화됐는지 확인합니다.

### Gradle Wrapper 실행 권한 오류가 발생하는 경우

최신 `develop`을 받은 뒤 다시 실행합니다. Linux와 macOS에서 계속 발생하면 다음 명령의 권한이 `100755`인지 확인합니다.

```bash
git ls-files --stage gradlew
```

### 기존 볼륨 때문에 초기화가 필요한 경우

필요한 데이터가 없는지 확인한 뒤 `docker compose down -v`를 실행합니다. 이 명령은 전체 로컬 데이터를 삭제하며 복구 기능을 제공하지 않습니다.

## 11. 작업 순서

작업 시작:

```cmd
git switch develop
git pull origin develop
git switch -c type/이슈번호-작업명
docker compose up -d --wait --wait-timeout 120
```

작업 종료:

```cmd
.\gradlew test --no-daemon
git status
docker compose down
```

설정이나 명령이 실제 저장소와 다르면 개인 환경에서만 우회하지 말고 팀에 공유한 뒤 문서와 설정을 함께 수정합니다.
