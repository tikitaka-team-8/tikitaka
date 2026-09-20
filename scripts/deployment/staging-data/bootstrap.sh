#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'root 권한으로 실행해야 합니다.' >&2
  exit 1
fi

if [[ ! -r /etc/os-release ]]; then
  echo '운영체제 정보를 확인할 수 없습니다.' >&2
  exit 1
fi

source /etc/os-release
if [[ "${ID:-}" != 'amzn' || "${VERSION_ID:-}" != '2023' || "$(uname -m)" != 'x86_64' ]]; then
  echo 'Amazon Linux 2023 x86_64에서만 실행할 수 있습니다.' >&2
  exit 1
fi

dnf install -y docker curl
systemctl enable --now docker

# 서버 재구성 시 동일 환경 재현을 위한 Docker Compose 플러그인 버전 고정
compose_version='v5.5.0'
plugin_dir='/usr/local/lib/docker/cli-plugins'
plugin_path="${plugin_dir}/docker-compose"
download_path="$(mktemp)"
trap 'rm -f "${download_path}"' EXIT

install -d -m 0755 "${plugin_dir}"
curl --fail --location --silent --show-error \
  "https://github.com/docker/compose/releases/download/${compose_version}/docker-compose-linux-x86_64" \
  --output "${download_path}"
install -m 0755 "${download_path}" "${plugin_path}"

docker --version
docker compose version
install -d -m 0700 /opt/tikitaka/staging-data

echo 'Docker 준비가 끝났습니다. Compose 파일을 배치한 뒤 deploy.sh를 실행하세요.'
