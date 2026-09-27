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
compose_file="${script_dir}/docker-compose.staging-monitoring.yml"
if [[ ! -f "${compose_file}" ]]; then
  echo "Compose 파일을 찾을 수 없습니다: ${compose_file}" >&2
  exit 1
fi

metadata_url='http://169.254.169.254/latest'
metadata_token="$(curl --fail --silent --show-error --max-time 5 \
  --request PUT "${metadata_url}/api/token" \
  --header 'X-aws-ec2-metadata-token-ttl-seconds: 60')"
aws_region="$(curl --fail --silent --show-error --max-time 5 \
  --header "X-aws-ec2-metadata-token: ${metadata_token}" \
  "${metadata_url}/meta-data/placement/region")"
if [[ -z "${aws_region}" ]]; then
  echo 'EC2 리전을 확인할 수 없습니다.' >&2
  exit 1
fi

read_parameter() {
  local parameter_name="$1"
  local value
  value="$(aws ssm get-parameter \
    --region "${aws_region}" \
    --name "${parameter_name}" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text)"
  if [[ -z "${value}" || "${value}" == 'None' ]]; then
    echo "모니터링 파라미터가 비어 있습니다: ${parameter_name}" >&2
    return 1
  fi
  printf '%s' "${value}"
}

GRAFANA_ADMIN_USERNAME="$(read_parameter '/tikitaka/staging/monitoring/grafana-admin-username')"
GRAFANA_ADMIN_PASSWORD="$(read_parameter '/tikitaka/staging/monitoring/grafana-admin-password')"
SLACK_WEBHOOK_URL="$(read_parameter '/tikitaka/staging/monitoring/slack-webhook')"
SLACK_RECIPIENT="$(read_parameter '/tikitaka/staging/monitoring/slack-recipient')"
export GRAFANA_ADMIN_USERNAME GRAFANA_ADMIN_PASSWORD SLACK_WEBHOOK_URL SLACK_RECIPIENT

data_root='/opt/tikitaka/staging-monitoring/data'
if ! mountpoint -q "${data_root}"; then
  echo "Monitoring EBS가 마운트되어 있지 않습니다: ${data_root}" >&2
  exit 1
fi

install -d -m 0750 -o 65534 -g 65534 "${data_root}/prometheus"
install -d -m 0750 -o 472 -g 0 "${data_root}/grafana"

docker compose -f "${compose_file}" config --quiet
docker compose -f "${compose_file}" up -d --wait --wait-timeout 180

curl --fail --silent --show-error http://127.0.0.1:9090/-/ready >/dev/null
curl --fail --silent --show-error http://127.0.0.1:3000/api/health >/dev/null

up_target_count=0
for ((attempt=1; attempt<=18; attempt++)); do
  targets_json="$(curl --fail --silent --show-error http://127.0.0.1:9090/api/v1/targets)"
  up_target_count="$( (grep -o '"health":"up"' <<<"${targets_json}" || true) | wc -l)"
  if ((up_target_count >= 4)); then
    break
  fi
  sleep 10
done
if ((up_target_count < 4)); then
  echo "정상 Prometheus target이 4개보다 적습니다: ${up_target_count}" >&2
  exit 1
fi

docker compose -f "${compose_file}" ps
echo '스테이징 Prometheus와 Grafana 배포 및 target 확인이 완료됐습니다.'
