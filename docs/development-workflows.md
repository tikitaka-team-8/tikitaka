# TIKITAKA 개발 실행 가이드

개발 범위에 맞는 실행 방식을 선택합니다. 환경을 처음 구성한다면 먼저 [개발 환경 설정](./development-environment-setup.md)을 확인합니다.

## Case 1. 전체 서비스를 Compose로 실행

다른 팀원의 서비스를 별도로 설정하지 않고 전체 연동 환경을 실행할 때 사용합니다.

```powershell
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

API 요청은 Gateway `http://localhost:8000`으로 보냅니다. `8081`~`8083` 포트는 로컬 개발 확인과 Prometheus 수집을 위해 loopback에만 공개됩니다.

## Case 2. 담당 서비스를 IntelliJ에서 개발

수정할 서비스와 Gateway는 IntelliJ에서 실행하고, 나머지 애플리케이션은 Compose로 실행합니다. 이렇게 하면 다른 팀원의 Run Configuration을 만들지 않고도 연동 개발을 할 수 있습니다.

예를 들어 Platform Service를 개발한다면:

```powershell
docker compose up -d --wait `
  platform-postgres platform-redis `
  ticketing-postgres ticketing-redis `
  payment-postgres kafka `
  ticketing-service payment-notification-service
```

IntelliJ에서 Platform Service와 Gateway를 다음 설정으로 실행합니다.

- EnvFile: 저장소 루트 `.env`
- Active profile: `local`

IntelliJ Gateway는 `localhost:8081`의 Platform과 Compose가 공개한 `localhost:8082`, `localhost:8083`의 서비스를 호출합니다.

다른 서비스를 개발할 때도 같은 원칙을 적용합니다.

1. 담당 서비스와 Gateway는 Compose에서 제외합니다.
2. 필요한 DB·Redis·Kafka와 나머지 애플리케이션을 Compose로 실행합니다.
3. 담당 서비스와 Gateway를 IntelliJ `local` 프로파일로 실행합니다.

이미 동일 서비스의 컨테이너가 실행 중이라면 포트 충돌을 막기 위해 중지합니다.

```powershell
docker compose stop platform-service gateway
```

## Case 3. 담당 서비스와 인프라만 실행

서비스 간 연동이 필요하지 않을 때 사용합니다.

Platform Service:

```powershell
docker compose up -d --wait platform-postgres platform-redis kafka
```

Ticketing Service:

```powershell
docker compose up -d --wait ticketing-postgres ticketing-redis kafka
```

Payment & Notification Service:

```powershell
docker compose up -d --wait payment-postgres kafka
```

애플리케이션은 IntelliJ에서 EnvFile과 `local` 프로파일을 지정해 실행합니다.

## Case 4. 변경한 컨테이너만 다시 빌드

```powershell
docker compose up -d --build --no-deps ticketing-service
docker compose ps ticketing-service
```

의존 인프라가 중지되어 있다면 `--no-deps`를 빼고 실행합니다.

## 상태와 로그 확인

Redis 키와 TTL은 <http://localhost:5540>의 RedisInsight에서 확인할 수 있습니다. RedisInsight 컨테이너에서는 `platform-redis:6379`, `ticketing-redis:6379`로 연결합니다.

```powershell
docker compose ps
docker compose logs -f gateway
docker compose logs -f ticketing-service
```

애플리케이션 health 주소:

- Gateway: <http://localhost:8000/actuator/health>
- Platform: <http://localhost:8081/actuator/health>
- Ticketing: <http://localhost:8082/actuator/health>
- Payment & Notification: <http://localhost:8083/actuator/health>

Prometheus target은 <http://localhost:9090/targets>에서 확인합니다.

## 종료와 데이터 초기화

컨테이너만 종료하고 데이터는 보존합니다.

```powershell
docker compose down
```

다음 명령은 PostgreSQL, Redis, Kafka, Prometheus와 Grafana의 로컬 데이터를 모두 삭제합니다.

```powershell
docker compose down -v
```

`down -v`는 복구 명령이 아닙니다. 필요한 데이터가 없고 명확한 초기화 목적이 있을 때만 실행합니다.

## 자주 발생하는 문제

### 포트가 이미 사용 중인 경우

같은 애플리케이션이 IntelliJ와 Compose에서 동시에 실행 중인지 확인합니다. 필요한 경우 해당 컨테이너를 중지합니다.

### DB 연결에 실패하는 경우

- `docker compose ps`에서 PostgreSQL이 `healthy`인지 확인합니다.
- IntelliJ 실행은 `local`, 컨테이너 실행은 `docker` 프로파일인지 확인합니다.
- `.env`의 DB 이름·계정·비밀번호가 Compose 설정과 일치하는지 확인합니다.

### IntelliJ에서 환경변수를 찾지 못하는 경우

- EnvFile 플러그인이 활성화됐는지 확인합니다.
- Run Configuration에서 저장소 루트 `.env`를 선택했는지 확인합니다.
- 설정을 변경한 뒤 애플리케이션을 완전히 재시작합니다.

### Gateway가 시작되지 않는 경우

`.env`의 `JWT_SECRET`이 Base64 형식이며 디코딩 후 32바이트 이상인지 확인합니다.

### Flyway 체크섬이 일치하지 않는 경우

적용된 마이그레이션 파일을 수정하거나 임의로 `repair`하지 않습니다. 기존 볼륨과 현재 브랜치의 마이그레이션 이력을 확인하고, 데이터 초기화가 가능한 상태에서만 `docker compose down -v`를 사용합니다.

