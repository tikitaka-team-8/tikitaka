#!/usr/bin/env bash
set -Eeuo pipefail
set +x

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'root 권한으로 실행해야 합니다.' >&2
  exit 1
fi

for command_name in aws curl docker; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "필요한 명령을 찾을 수 없습니다: ${command_name}" >&2
    exit 1
  fi
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
compose_file="${script_dir}/docker-compose.staging-data.yml"
if [[ ! -f "${compose_file}" ]]; then
  echo "Compose 파일을 찾을 수 없습니다: ${compose_file}" >&2
  exit 1
fi

# IMDSv2를 통한 현재 EC2의 리전 및 사설 IP 조회
metadata_url='http://169.254.169.254/latest'
metadata_token="$(curl --fail --silent --show-error --max-time 5 \
  --request PUT "${metadata_url}/api/token" \
  --header 'X-aws-ec2-metadata-token-ttl-seconds: 60')"
if [[ -z "${metadata_token}" ]]; then
  echo 'EC2 메타데이터 토큰을 받을 수 없습니다.' >&2
  exit 1
fi

metadata_header="X-aws-ec2-metadata-token: ${metadata_token}"
aws_region="$(curl --fail --silent --show-error --max-time 5 \
  --header "${metadata_header}" "${metadata_url}/meta-data/placement/region")"
STAGING_DATA_PRIVATE_IP="$(curl --fail --silent --show-error --max-time 5 \
  --header "${metadata_header}" "${metadata_url}/meta-data/local-ipv4")"
if [[ -z "${aws_region}" || -z "${STAGING_DATA_PRIVATE_IP}" ]]; then
  echo 'EC2 리전 또는 사설 IP를 확인할 수 없습니다.' >&2
  exit 1
fi
export STAGING_DATA_PRIVATE_IP

export PLATFORM_DB_NAME="${PLATFORM_DB_NAME:-tikitaka_platform}"
export PLATFORM_DB_USERNAME="${PLATFORM_DB_USERNAME:-platform}"
export TICKETING_DB_NAME="${TICKETING_DB_NAME:-tikitaka_ticketing}"
export TICKETING_DB_USERNAME="${TICKETING_DB_USERNAME:-ticketing}"
export PAYMENT_DB_NAME="${PAYMENT_DB_NAME:-tikitaka_payment}"
export PAYMENT_DB_USERNAME="${PAYMENT_DB_USERNAME:-payment}"

read_password() {
  local parameter_name="$1"
  local value
  value="$(aws ssm get-parameter \
    --region "${aws_region}" \
    --name "${parameter_name}" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text)"
  if [[ -z "${value}" || "${value}" == 'None' ]]; then
    echo "DB 암호 파라미터가 비어 있습니다: ${parameter_name}" >&2
    return 1
  fi
  printf '%s' "${value}"
}

PLATFORM_DB_PASSWORD="$(read_password '/tikitaka/staging/data/platform-db-password')"
TICKETING_DB_PASSWORD="$(read_password '/tikitaka/staging/data/ticketing-db-password')"
PAYMENT_DB_PASSWORD="$(read_password '/tikitaka/staging/data/payment-db-password')"
export PLATFORM_DB_PASSWORD TICKETING_DB_PASSWORD PAYMENT_DB_PASSWORD

docker compose -f "${compose_file}" config --quiet
docker compose -f "${compose_file}" up -d --wait --wait-timeout 180
docker compose -f "${compose_file}" ps

echo '스테이징 데이터 서비스 기동과 healthcheck가 완료됐습니다.'
