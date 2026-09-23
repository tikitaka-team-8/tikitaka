# CD 파이프라인

## 개요

TIKITAKA의 스테이징 배포는 GitHub Actions와 AWS를 사용합니다. 애플리케이션과 데이터 계층은 변경 주기와 상태 관리 방식이 다르므로 별도의 워크플로우로 배포합니다.

| 구분 | 실행 조건 | 배포 대상 | 워크플로우 |
| --- | --- | --- | --- |
| 애플리케이션 배포 | `develop` 브랜치 push 또는 수동 실행 | ECS Fargate 서비스 4개 | [cd.yml](../.github/workflows/cd.yml) |
| 데이터 계층 배포 | `develop` 브랜치에서 수동 실행 | 데이터 EC2의 Docker Compose | [deploy-staging-data.yml](../.github/workflows/deploy-staging-data.yml) |

```text
develop 브랜치 반영
        │
        └─ Deploy ECS
             ├─ 서비스별 Docker 이미지 빌드
             ├─ Git SHA 태그로 Amazon ECR 게시
             ├─ ECS 작업 정의 개정
             ├─ ECS 서비스 순차 업데이트
             └─ 공개 API Smoke Test

필요할 때 수동 실행
        │
        └─ Deploy Staging Data
             ├─ Compose 파일과 배포 스크립트를 Amazon S3에 게시
             ├─ AWS Systems Manager로 EC2에 명령 전달
             └─ PostgreSQL·Redis·Kafka 기동 및 상태 확인
```

애플리케이션 코드는 `develop` 반영 시 자동 배포합니다. PostgreSQL, Redis, Kafka처럼 상태를 저장하는 데이터 계층은 의도하지 않은 재시작을 방지하기 위해 필요한 경우에만 수동으로 배포합니다.

---

## AWS 인증 방식

GitHub에 장기 Access Key를 저장하지 않습니다. GitHub Actions가 발급한 OIDC 토큰으로 AWS IAM 역할을 맡아 실행할 때만 임시 자격 증명을 얻습니다.

역할은 수행 범위에 따라 분리되어 있습니다.

| GitHub 저장소 변수 | 역할 |
| --- | --- |
| `STAGING_IMAGE_PUBLISHER_ROLE_ARN` | 스테이징 ECR 이미지 조회 및 게시 |
| `STAGING_DEPLOYER_ROLE_ARN` | ECS 작업 정의 등록과 서비스 업데이트 |
| `STAGING_DATA_DEPLOYER_ROLE_ARN` | S3 배포 파일 게시와 SSM 명령 실행 |

IAM 신뢰 정책은 이 저장소의 `develop` 브랜치에서 발급된 OIDC 토큰만 허용합니다. DB 비밀번호, JWT Secret과 내부 서비스 키는 GitHub Actions 파일이나 Docker 이미지에 포함하지 않고 AWS Systems Manager Parameter Store에서 관리합니다.

---

## 애플리케이션 배포

### 대상 서비스

| 서비스 | ECR 저장소 | ECS 서비스 |
| --- | --- | --- |
| Gateway | `tikitaka/staging/gateway` | `gateway` |
| Platform | `tikitaka/staging/platform-service` | `platform-service` |
| Ticketing | `tikitaka/staging/ticketing-service` | `ticketing-service` |
| Payment & Notification | `tikitaka/staging/payment-notification-service` | `payment-notification-service` |

### 1. 이미지 빌드와 게시

네 서비스는 GitHub Actions의 Matrix 작업으로 병렬 처리됩니다. 각 서비스의 Dockerfile로 이미지를 빌드한 뒤 Amazon ECR에 게시합니다.

이미지 태그에는 워크플로우를 실행한 전체 Git commit SHA를 사용합니다.

```text
<AWS 계정>.dkr.ecr.ap-northeast-2.amazonaws.com/
    tikitaka/staging/<서비스>:<Git commit SHA>
```

ECR 저장소는 이미지 태그 변경을 허용하지 않습니다. 따라서 하나의 태그는 항상 동일한 코드 버전을 가리키며, 배포된 코드와 Git commit을 추적할 수 있습니다. 같은 SHA 이미지가 이미 존재하면 다시 빌드하지 않고 기존 이미지를 사용합니다.

### 2. ECS 순차 배포

이미지가 모두 준비되면 [staging-app/deploy.sh](../scripts/deployment/staging-app/deploy.sh)가 각 서비스의 현재 작업 정의를 읽습니다. 컨테이너 이미지만 새 SHA로 교체한 작업 정의 개정을 등록하고 ECS 서비스를 업데이트합니다.

```text
Platform
   ↓ 안정 상태 확인
Ticketing
   ↓ 안정 상태 확인
Payment & Notification
   ↓ 안정 상태 확인
Gateway
   ↓ 안정 상태 확인
Smoke Test
```

각 서비스는 `aws ecs wait services-stable`로 안정 상태가 확인되어야 다음 서비스로 넘어갑니다. 중간 서비스가 안정화되지 않으면 이후 서비스는 배포하지 않고 워크플로우가 실패합니다. 동일 환경에 여러 배포가 겹치지 않도록 동시 실행도 제한합니다.

Platform부터 배포하고 외부 진입점인 Gateway를 마지막에 배포해 하위 서비스가 준비된 뒤 새 Gateway 버전이 요청을 받도록 합니다.

### 3. 배포 전후 검증

배포 전과 배포 후에 API Gateway 기본 주소로 다음 요청을 실행합니다.

| 요청 | 성공 조건 |
| --- | --- |
| `GET /actuator/health` | 응답의 `status`가 `UP` |
| `GET /api/v1/events` | HTTP 상태와 공통 응답의 `status`가 `200`, `code`가 `SUCCESS` |

배포 전 검증은 데이터 EC2가 중지됐거나 기존 서비스가 정상 동작하지 않는 상태에서 서비스 교체가 시작되는 것을 막습니다. 배포 후 검증은 Gateway를 통한 외부 요청이 정상 처리되는지 확인합니다.

---

## 데이터 EC2 배포

데이터 계층은 한 대의 EC2에서 Docker Compose로 실행합니다.

- PostgreSQL 3개: Platform, Ticketing, Payment 전용
- Redis 2개: Platform, Ticketing 전용
- Kafka 1개

### 동작 방식

GitHub Actions에서 `Deploy Staging Data`를 수동 실행하면 다음 순서로 배포합니다.

```text
1. CloudFormation 스택에서 EC2 인스턴스 ID와 S3 버킷 조회
2. Git SHA별 S3 경로에 Compose 파일과 deploy.sh 업로드
3. SSM Run Command로 데이터 EC2에 배포 명령 전달
4. EC2가 해당 Git SHA의 파일을 S3에서 다운로드
5. Parameter Store에서 DB 비밀번호 조회
6. docker compose config 검사
7. docker compose up -d --wait 실행
8. 모든 컨테이너의 기동 및 healthcheck 상태 확인
```

배포 파일은 다음과 같이 commit SHA별로 구분합니다.

```text
s3://<스테이징 배포 버킷>/staging-data/releases/<Git commit SHA>/
```

EC2에 SSH 인바운드를 열거나 GitHub Actions 러너에서 직접 접속하지 않습니다. AWS Systems Manager가 원격 명령을 전달하며, 명령의 표준 출력과 오류는 GitHub Actions 로그에서 확인할 수 있습니다.

[staging-data/deploy.sh](../scripts/deployment/staging-data/deploy.sh)는 EC2 메타데이터 서비스 IMDSv2에서 현재 리전과 사설 IP를 조회합니다. DB 비밀번호는 Parameter Store에서 복호화해 프로세스 환경변수로만 전달하며 `.env` 파일이나 저장소에 기록하지 않습니다.

SSM 명령이 실패하거나 제한 시간 안에 끝나지 않으면 워크플로우도 실패합니다.

---

## 배포 실패와 복구

### 애플리케이션

ECS 배포 중 실패하면 GitHub Actions 로그와 해당 서비스의 CloudWatch Logs를 먼저 확인합니다. 실패한 서비스 이후의 순차 배포는 진행되지 않습니다.

현재 자동 롤백 워크플로우는 없습니다. 복구가 필요하면 문제를 수정하거나 정상 commit으로 되돌린 뒤 `develop`에 반영하여 새 SHA로 다시 배포합니다. ECR에는 SHA별 이미지가 남으므로 배포 버전과 원인을 추적할 수 있습니다.

### 데이터 계층

데이터 배포 실패 시 GitHub Actions에 표시된 SSM Command ID와 실행 로그를 확인합니다. 같은 commit의 워크플로우를 다시 실행하면 동일한 S3 릴리스 파일로 Compose를 재적용할 수 있습니다.

데이터 삭제가 발생할 수 있는 `docker compose down -v`는 배포 스크립트에서 실행하지 않습니다. 데이터 백업·복원과 Flyway 장애 복구 훈련은 현재 CD 범위에 포함되지 않습니다.

---

## 관련 파일

| 파일 | 역할 |
| --- | --- |
| [.github/workflows/cd.yml](../.github/workflows/cd.yml) | ECR 이미지 게시와 ECS 자동 배포 |
| [.github/workflows/deploy-staging-data.yml](../.github/workflows/deploy-staging-data.yml) | 데이터 EC2 수동 배포 |
| [scripts/deployment/staging-app/deploy.sh](../scripts/deployment/staging-app/deploy.sh) | ECS 작업 정의 갱신과 Smoke Test |
| [scripts/deployment/staging-data/deploy.sh](../scripts/deployment/staging-data/deploy.sh) | 데이터 컨테이너 구성 검사와 실행 |
| [docker-compose.staging-data.yml](../docker-compose.staging-data.yml) | 스테이징 데이터 계층 정의 |
| [infra/aws/staging-images.yaml](../infra/aws/staging-images.yaml) | ECR 저장소, GitHub OIDC 및 배포 IAM 역할 |
| [infra/aws/staging-app.yaml](../infra/aws/staging-app.yaml) | ECS 애플리케이션 인프라 |
| [infra/aws/staging-data.yaml](../infra/aws/staging-data.yaml) | VPC와 데이터 EC2 인프라 |
