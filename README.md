# TIKITAKA

공연 탐색부터 대기열, 좌석 선점, 예매, 결제와 알림까지 제공하는 MSA 기반 공연 예매 프로젝트입니다.

## 프로젝트 구성

| 모듈 | 담당 영역 | 포트 |
| --- | --- | ---: |
| Gateway | 외부 API 진입점, 인증, 라우팅 | 8000 |
| Platform Service | User, Auth, Organizer, Event, Venue, Session | 8081 |
| Ticketing Service | Queue, Seat, Seat Hold, Reservation | 8082 |
| Payment & Notification Service | Payment, Refund, Notification | 8083 |

## 기술 환경

- Java 21
- Spring Boot 3.5.16
- Spring Cloud 2025.0.3
- Gradle 멀티 프로젝트
- PostgreSQL, Redis, Kafka
- Docker Compose

## 로컬 개발 빠른 시작

### 1. 필요한 프로그램

- Git
- JDK 21
- Docker Desktop
- IntelliJ IDEA 권장
- IntelliJ EnvFile 플러그인

IntelliJ의 Project SDK와 Gradle JVM도 Java 21로 설정해야 합니다.

### 2. 저장소 준비

```cmd
git clone https://github.com/tikitaka-team-8/tikitaka.git
cd tikitaka
git switch develop
```

### 3. 팀 로컬 환경변수 준비

공유된 `.env`를 받아 저장소 루트에 저장합니다. 필요한 환경변수의 이름과 공개 예시는 `.env.example`에서 확인할 수 있습니다.

```text
tikitaka/
├─ .env
├─ .env.example
└─ docker-compose.yml
```

`.env`는 Git에 포함하지 않습니다. Docker Compose는 루트 `.env`를 자동으로 읽고, IntelliJ 애플리케이션에는 EnvFile 플러그인으로 같은 파일을 주입합니다.

### 4. 공통 인프라 실행

```cmd
docker compose config --quiet
docker compose up -d --wait --wait-timeout 120
docker compose ps
```

### 5. 애플리케이션 실행

평소에는 PostgreSQL, Redis, Kafka 등 공통 인프라만 Docker Compose로 실행합니다. Spring Boot 애플리케이션은 IntelliJ에서 실행하며 다음 두 가지를 Run Configuration에 설정합니다.

- EnvFile에서 저장소 루트의 `.env` 선택
- Active profiles에 `local` 지정

```text
SPRING_PROFILES_ACTIVE=local
```

또는 Program arguments를 사용할 수 있습니다.

```text
--spring.profiles.active=local
```

### 6. 테스트 실행

Windows:

```cmd
.\gradlew test --no-daemon
```

macOS 또는 Linux:

```bash
./gradlew test --no-daemon
```

테스트는 `test` 프로파일과 PostgreSQL Testcontainers를 사용하므로 `local`, `docker`, `.env`에 의존하지 않습니다. Docker Desktop이 실행 중이어야 하며 서비스별 테스트 PostgreSQL은 자동으로 생성되고 종료됩니다.

- 단위 테스트는 Spring Context와 외부 인프라 없이 작성합니다.
- DB 통합 테스트는 각 서비스의 `@PostgresIntegrationTest`를 사용합니다.
- 테스트에 DB URL, 계정, 비밀번호나 고정 포트를 작성하지 않습니다.
- Redis와 Kafka 테스트 인프라는 실제 연동 테스트가 필요할 때 추가 예정입니다.

## 주요 로컬 주소

| 구성 요소 | 주소 |
| --- | --- |
| Platform PostgreSQL | `localhost:5433` |
| Ticketing PostgreSQL | `localhost:5434` |
| Payment PostgreSQL | `localhost:5435` |
| Platform Redis | `localhost:6380` |
| Ticketing Redis | `localhost:6381` |
| Kafka | `localhost:9092` |
| Kafka UI | <http://localhost:8085> |
| Prometheus | <http://localhost:9090> |
| Grafana | <http://localhost:3000> |

서비스는 자신의 PostgreSQL과 Redis만 사용합니다. Payment & Notification Service는 Redis를 사용하지 않습니다.

## 상세 안내

IntelliJ 설정, 서비스별 연결 정보 등에 관한 구체적인 사항은 [로컬 개발 환경 설정 가이드](docs/development-environment-setup.md)를 확인해 주세요.
