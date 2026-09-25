# CD 파이프라인

## 개요

TIKITAKA의 스테이징 배포는 GitHub Actions와 AWS를 사용합니다. 애플리케이션, 데이터 계층과 모니터링 계층은 변경 주기와 상태 관리 방식이 다르므로 별도의 워크플로우로 배포합니다.

| 구분 | 실행 조건 | 배포 대상 | 워크플로우 |
| --- | --- | --- | --- |
| 애플리케이션 배포 | `develop` 브랜치 push 또는 수동 실행 | ECS Fargate 서비스 4개 | [cd.yml](../.github/workflows/cd.yml) |
| 데이터 계층 배포 | `develop` 브랜치에서 수동 실행 | 데이터 EC2의 Docker Compose | [deploy-staging-data.yml](../.github/workflows/deploy-staging-data.yml) |
| 모니터링 계층 배포 | `develop` 브랜치에서 수동 실행 | 모니터링 EC2의 Prometheus와 Grafana | [deploy-staging-monitoring.yml](../.github/workflows/deploy-staging-monitoring.yml) |

```text
develop 브랜치 반영
        │
        └─ Deploy ECS
             ├─ 서비스별 Docker 이미지 빌드
             ├─ Git SHA 태그로 Amazon ECR 게시
             ├─ ECS 작업 정의 개정
             └─ ECS 서비스 순차 업데이트 및 안정 상태 확인

필요할 때 수동 실행
        │
        └─ Deploy Staging Data
             ├─ 초기화·Compose·배포 스크립트를 Amazon S3에 게시
             ├─ AWS Systems Manager로 EC2에 명령 전달
             └─ PostgreSQL·Redis·Kafka 기동 및 상태 확인

필요할 때 수동 실행
        │
        └─ Deploy Staging Monitoring
             ├─ 초기화·Compose·설정·배포 파일을 Amazon S3에 게시
             ├─ AWS Systems Manager로 EC2에 명령 전달
             └─ Prometheus·Grafana 기동 및 수집 대상 확인
```

애플리케이션 코드는 `develop` 반영 시 자동 배포합니다. 상태를 저장하는 데이터 계층과 메트릭을 보존하는 모니터링 계층은 의도하지 않은 재시작을 방지하기 위해 필요한 경우에만 수동으로 배포합니다.

---

## AWS 인증 방식

GitHub에 장기 Access Key를 저장하지 않습니다. GitHub Actions가 발급한 OIDC 토큰으로 AWS IAM 역할을 맡아 실행할 때만 임시 자격 증명을 얻습니다.

역할은 수행 범위에 따라 분리되어 있습니다.

| GitHub 저장소 변수 | 역할 |
| --- | --- |
| `STAGING_IMAGE_PUBLISHER_ROLE_ARN` | 스테이징 ECR 이미지 조회 및 게시 |
| `STAGING_DEPLOYER_ROLE_ARN` | ECS 작업 정의 등록과 서비스 업데이트 |
| `STAGING_DATA_DEPLOYER_ROLE_ARN` | S3 배포 파일 게시와 SSM 명령 실행 |
| `STAGING_MONITORING_DEPLOYER_ROLE_ARN` | 모니터링 배포 파일 게시와 SSM 명령 실행 |

IAM 신뢰 정책은 이 저장소의 `develop` 브랜치에서 발급된 OIDC 토큰만 허용합니다. DB 비밀번호, JWT Secret, 내부 서비스 키, Grafana 관리자 계정과 Slack Webhook은 GitHub Actions 파일이나 Docker 이미지에 포함하지 않고 AWS Systems Manager Parameter Store에서 관리합니다.

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
```

각 서비스는 `aws ecs wait services-stable`로 안정 상태가 확인되어야 다음 서비스로 넘어갑니다. 중간 서비스가 안정화되지 않으면 이후 서비스는 배포하지 않고 워크플로우가 실패합니다. 동일 환경에 여러 배포가 겹치지 않도록 동시 실행도 제한합니다.

Platform부터 배포하고 외부 진입점인 Gateway를 마지막에 배포해 하위 서비스가 준비된 뒤 새 Gateway 버전이 요청을 받도록 합니다.

## 데이터 EC2 배포

데이터 계층은 한 대의 EC2에서 Docker Compose로 실행합니다.

- PostgreSQL 3개: Platform, Ticketing, Payment 전용
- Redis 2개: Platform, Ticketing 전용
- Kafka 1개

### 동작 방식

GitHub Actions에서 `Deploy Staging Data`를 수동 실행하면 다음 순서로 배포합니다.

```text
1. CloudFormation 스택에서 EC2 인스턴스 ID와 S3 버킷 조회
2. Git SHA별 S3 경로에 bootstrap.sh, Compose 파일과 deploy.sh 업로드
3. SSM Run Command로 데이터 EC2에 배포 명령 전달
4. EC2가 해당 Git SHA의 파일을 S3에서 다운로드
5. bootstrap.sh로 Docker와 고정 버전의 Docker Compose 준비
6. Parameter Store에서 DB 비밀번호 조회
7. docker compose config 검사
8. docker compose up -d --wait 실행
9. 모든 컨테이너의 기동 및 healthcheck 상태 확인
```

배포 파일은 다음과 같이 commit SHA별로 구분합니다.

```text
s3://<스테이징 배포 버킷>/staging-data/releases/<Git commit SHA>/
```

EC2에 SSH 인바운드를 열거나 GitHub Actions 러너에서 직접 접속하지 않습니다. AWS Systems Manager가 원격 명령을 전달하며, 명령의 표준 출력과 오류는 GitHub Actions 로그에서 확인할 수 있습니다.

[staging-data/deploy.sh](../scripts/deployment/staging-data/deploy.sh)는 EC2 메타데이터 서비스 IMDSv2에서 현재 리전과 사설 IP를 조회합니다. DB 비밀번호는 Parameter Store에서 복호화해 프로세스 환경변수로만 전달하며 `.env` 파일이나 저장소에 기록하지 않습니다.

SSM 명령이 실패하거나 제한 시간 안에 끝나지 않으면 워크플로우도 실패합니다.

---

## 모니터링 EC2 배포

모니터링 계층은 데이터 EC2와 분리된 EC2에서 Docker Compose로 실행합니다.

- Prometheus: Cloud Map 사설 DNS로 ECS Fargate 서비스 4개의 Actuator 메트릭 수집
- Grafana: Prometheus 데이터소스, 대시보드와 CPU Slack 알림 자동 등록
- Monitoring EBS: Prometheus TSDB와 Grafana 데이터 보존
- CloudWatch Logs: ECS 애플리케이션 로그 보존

Prometheus와 Grafana의 호스트 포트는 `127.0.0.1`에만 바인딩합니다. Grafana는 인터넷에 공개하지 않고 Systems Manager 포트 포워딩을 통해 접근합니다.

### 동작 방식

GitHub Actions에서 `Deploy Staging Monitoring`을 수동 실행하면 다음 순서로 배포합니다.

```text
1. 데이터·모니터링 CloudFormation 스택에서 S3 버킷, EC2 인스턴스 ID와 EBS 볼륨 ID 조회
2. Git SHA별 S3 경로에 bootstrap.sh, Compose, Prometheus·Grafana 설정과 deploy.sh 업로드
3. SSM Run Command로 모니터링 EC2에 배포 명령 전달
4. bootstrap.sh로 Docker·Docker Compose 준비 및 Monitoring EBS 안전 마운트
5. Parameter Store에서 Grafana 관리자 계정과 Slack 설정 조회
6. docker compose config 검사 및 Prometheus·Grafana 기동
7. Prometheus·Grafana healthcheck와 정상 수집 대상 4개 확인
```

Prometheus 데이터는 15일과 20GB 중 먼저 도달하는 기준까지 보존합니다. Monitoring EBS는 CloudFormation 스택 삭제와 교체 시에도 `Retain` 정책으로 유지합니다.

모니터링 서버만을 위한 시작·중지 워크플로우는 두지 않습니다. 스테이징 전체의 비용 제어 정책은 데이터·애플리케이션 서버와 함께 별도로 다룹니다.

---

## 배포 실패와 복구

### 애플리케이션

ECS 배포 중 실패하면 GitHub Actions 로그와 해당 서비스의 CloudWatch Logs를 먼저 확인합니다. 실패한 서비스 이후의 순차 배포는 진행되지 않습니다.

현재 자동 롤백 워크플로우는 없습니다. 복구가 필요하면 문제를 수정하거나 정상 commit으로 되돌린 뒤 `develop`에 반영하여 새 SHA로 다시 배포합니다. ECR에는 SHA별 이미지가 남으므로 배포 버전과 원인을 추적할 수 있습니다.

### 데이터 계층

데이터 배포 실패 시 GitHub Actions에 표시된 SSM Command ID와 실행 로그를 확인합니다. 같은 commit의 워크플로우를 다시 실행하면 동일한 S3 릴리스 파일로 Compose를 재적용할 수 있습니다.

데이터 삭제가 발생할 수 있는 `docker compose down -v`는 배포 스크립트에서 실행하지 않습니다. 데이터 백업·복원과 Flyway 장애 복구 훈련은 현재 CD 범위에 포함되지 않습니다.

### 모니터링 계층

모니터링 배포 실패 시 GitHub Actions에 표시된 SSM Command ID와 표준 출력·오류를 확인합니다. bootstrap 실패는 Docker 설치, Compose 체크섬 또는 EBS 장치·마운트 상태를 우선 확인합니다. deploy 실패는 컨테이너 healthcheck, Prometheus 수집 대상과 Parameter Store 값을 확인합니다.

같은 commit의 워크플로우를 다시 실행하면 멱등한 bootstrap과 Compose 구성을 재적용합니다. bootstrap은 파일시스템이 없는 신규 EBS만 XFS로 포맷하며, 기존 XFS 볼륨은 재포맷하지 않습니다.

---

## 관련 파일

| 파일 | 역할 |
| --- | --- |
| [.github/workflows/cd.yml](../.github/workflows/cd.yml) | ECR 이미지 게시와 ECS 자동 배포 |
| [.github/workflows/deploy-staging-data.yml](../.github/workflows/deploy-staging-data.yml) | 데이터 EC2 수동 배포 |
| [.github/workflows/deploy-staging-monitoring.yml](../.github/workflows/deploy-staging-monitoring.yml) | 모니터링 EC2 수동 배포 |
| [scripts/deployment/staging-app/deploy.sh](../scripts/deployment/staging-app/deploy.sh) | ECS 작업 정의 갱신과 서비스 안정 상태 확인 |
| [scripts/deployment/staging-data/bootstrap.sh](../scripts/deployment/staging-data/bootstrap.sh) | 데이터 EC2의 Docker와 Docker Compose 준비 |
| [scripts/deployment/staging-data/deploy.sh](../scripts/deployment/staging-data/deploy.sh) | 데이터 컨테이너 구성 검사와 실행 |
| [scripts/deployment/staging-monitoring/bootstrap.sh](../scripts/deployment/staging-monitoring/bootstrap.sh) | 모니터링 EC2의 Docker·Docker Compose·EBS 준비 |
| [scripts/deployment/staging-monitoring/deploy.sh](../scripts/deployment/staging-monitoring/deploy.sh) | Prometheus·Grafana 구성 검사와 실행 |
| [docker-compose.staging-data.yml](../docker-compose.staging-data.yml) | 스테이징 데이터 계층 정의 |
| [docker-compose.staging-monitoring.yml](../docker-compose.staging-monitoring.yml) | 스테이징 Prometheus·Grafana 정의 |
| [infra/aws/staging-images.yaml](../infra/aws/staging-images.yaml) | ECR 저장소, GitHub OIDC 및 배포 IAM 역할 |
| [infra/aws/staging-app.yaml](../infra/aws/staging-app.yaml) | ECS 애플리케이션 인프라 |
| [infra/aws/staging-data.yaml](../infra/aws/staging-data.yaml) | VPC와 데이터 EC2 인프라 |
| [infra/aws/staging-monitoring.yaml](../infra/aws/staging-monitoring.yaml) | 모니터링 EC2와 전용 EBS 인프라 |
