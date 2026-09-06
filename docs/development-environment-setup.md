# TIKITAKA 개발 환경 설정

이 문서는 처음 저장소를 받은 개발자가 로컬 환경을 준비하기 위한 안내서입니다. 실제 작업별 실행 명령은 [개발 실행 가이드](./development-workflows.md)를 참고합니다.

## 1. 개발 환경 구조

TIKITAKA는 두 가지 방식으로 애플리케이션을 실행합니다.

1. 전체 Compose 실행: Gateway와 세 도메인 서비스를 모두 컨테이너로 실행합니다.
2. IntelliJ 혼합 실행: 수정할 서비스와 Gateway는 IntelliJ에서 실행하고, 나머지 서비스와 인프라는 Compose로 실행합니다.

모든 외부 API 요청은 Gateway `http://localhost:8000`을 사용합니다.

```text
Client
  └─ Gateway :8000
       ├─ Platform Service :8081
       ├─ Ticketing Service :8082
       └─ Payment & Notification Service :8083
```

각 도메인 서비스는 자신이 소유한 DB와 Redis만 사용합니다. Payment & Notification Service는 Redis를 사용하지 않습니다.

## 2. 준비할 프로그램

| 도구 | 기준 |
| --- | --- |
| Git | 최신 안정 버전 권장 |
| JDK | Java 21 |
| Docker Desktop | Docker Compose v2 포함 |
| IntelliJ IDEA | Community 또는 Ultimate |
| EnvFile | IntelliJ에서 `.env`를 주입할 때 사용 |

IntelliJ의 Project SDK와 Gradle JVM도 Java 21로 설정합니다.

## 3. 저장소 준비

```powershell
git clone https://github.com/tikitaka-team-8/tikitaka.git
cd tikitaka
git switch develop
git pull origin develop
git switch -c type/이슈번호-작업명
```

작업 브랜치는 최신 `develop`에서 생성합니다. `main`과 `develop`에는 직접 Push하지 않습니다.

## 4. 환경변수 준비

팀에서 공유한 개발용 `.env`를 저장소 루트에 둡니다. 공유 파일이 없다면 예제 파일을 복사합니다.

```powershell
Copy-Item .env.example .env
```

`.env`는 Git에 포함하지 않습니다. 다음과 같이 실행 시 주입해야 하는 값만 관리합니다.

- DB 이름, 사용자명, 비밀번호
- JWT Secret과 토큰 만료시간
- Grafana 관리자 계정

DB·Redis·Kafka 주소와 포트는 `.env`에서 관리하지 않습니다.

## 5. 설정 파일의 역할

| 파일 | 역할 |
| --- | --- |
| `application.yaml` | 포트, JPA, Flyway, Actuator 등 공통 설정 |
| `application-local.yaml` | IntelliJ 실행용 `localhost` 주소 |
| `application-docker.yaml` | Compose 내부 서비스 주소 |
| `.env` | 계정, 비밀번호, JWT Secret |
| `docker-compose.yml` | 컨테이너, 네트워크, 볼륨, healthcheck와 환경변수 전달 |

`local`과 `docker` 프로파일이 주소를 결정합니다.

| 자원 | `local` | `docker` |
| --- | --- | --- |
| Platform PostgreSQL | `localhost:5433` | `platform-postgres:5432` |
| Ticketing PostgreSQL | `localhost:5434` | `ticketing-postgres:5432` |
| Payment PostgreSQL | `localhost:5435` | `payment-postgres:5432` |
| Platform Redis | `localhost:6380` | `platform-redis:6379` |
| Ticketing Redis | `localhost:6381` | `ticketing-redis:6379` |
| Kafka | `localhost:9092` | `kafka:19092` |

컨테이너 안에서 `localhost`는 해당 컨테이너 자신입니다. 컨테이너 간 통신에는 Compose 서비스명을 사용합니다.

## 6. 최초 확인

```powershell
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

모든 항목이 `running` 또는 `healthy`이면 준비가 끝난 것입니다. 실행 방식 변경, 특정 서비스 재빌드와 로그 확인은 [개발 실행 가이드](./development-workflows.md)를 참고합니다.

## 7. 테스트

```powershell
.\gradlew test --no-daemon
```

DB 통합 테스트는 PostgreSQL Testcontainers를 사용하므로 Docker Desktop이 실행 중이어야 합니다. 테스트는 `.env`, `local`, `docker` 프로파일과 고정 포트에 의존하지 않습니다.

## 8. 주요 주소

| 구성 요소 | 주소 |
| --- | --- |
| Gateway | <http://localhost:8000> |
| RedisInsight | <http://localhost:5540> |
| Kafka UI | <http://localhost:8085> |
| Prometheus | <http://localhost:9090> |
| Grafana | <http://localhost:3000> |

RedisInsight에는 Docker 네트워크 기준으로 다음 두 연결을 등록합니다.

| 이름 예시 | Host | Port |
| --- | --- | ---: |
| Platform Redis | `platform-redis` | `6379` |
| Ticketing Redis | `ticketing-redis` | `6379` |
