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

monitoring_volume_id="${1:-}"
if [[ ! "${monitoring_volume_id}" =~ ^vol-[0-9a-f]+$ ]]; then
  echo '첫 번째 인수로 MonitoringDataVolumeId(vol-...)를 전달해야 합니다.' >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo 'curl 명령을 찾을 수 없습니다.' >&2
  exit 1
fi

packages=()
command -v docker >/dev/null 2>&1 || packages+=(docker)
command -v mkfs.xfs >/dev/null 2>&1 || packages+=(xfsprogs)
if ((${#packages[@]} > 0)); then
  dnf install -y "${packages[@]}"
fi
systemctl enable --now docker

# 서버 재구성 시 동일 환경 재현을 위한 Docker Compose 플러그인 버전 고정
compose_version='v5.5.0'
compose_sha256='c57ab918abd5b05ca7e7d0f275875dd1330a695074f309dc9eab1b49efafcd4b'
plugin_dir='/usr/local/lib/docker/cli-plugins'
plugin_path="${plugin_dir}/docker-compose"

install -d -m 0755 "${plugin_dir}"
installed_sha256=''
if [[ -f "${plugin_path}" ]]; then
  installed_sha256="$(sha256sum "${plugin_path}" | awk '{print $1}')"
fi

if [[ "${installed_sha256}" != "${compose_sha256}" ]]; then
  download_path="$(mktemp)"
  trap 'rm -f "${download_path}"' EXIT
  curl --fail --location --silent --show-error \
    "https://github.com/docker/compose/releases/download/${compose_version}/docker-compose-linux-x86_64" \
    --output "${download_path}"
  echo "${compose_sha256}  ${download_path}" | sha256sum --check --status
  install -m 0755 "${download_path}" "${plugin_path}"
fi

# Nitro 인스턴스의 CloudFormation /dev/sdf와 NVMe 장치명 변환 대응
# EBS Volume ID와 NVMe serial 비교를 통한 정확한 데이터 볼륨 선택
expected_serial="${monitoring_volume_id//-/}"
data_device=''
for ((attempt=1; attempt<=60; attempt++)); do
  while read -r device serial; do
    normalized_serial="${serial,,}"
    normalized_serial="${normalized_serial//-/}"
    if [[ "${normalized_serial}" == "${expected_serial}" ]]; then
      data_device="${device}"
      break
    fi
  done < <(lsblk -dpno NAME,SERIAL 2>/dev/null)

  if [[ -n "${data_device}" ]]; then
    break
  fi
  sleep 2
done

if [[ -z "${data_device}" || ! -b "${data_device}" ]]; then
  echo "Monitoring EBS 장치를 찾을 수 없습니다: ${monitoring_volume_id}" >&2
  exit 1
fi

mount_point='/opt/tikitaka/staging-monitoring/data'
install -d -m 0700 /opt/tikitaka/staging-monitoring
install -d -m 0755 "${mount_point}"

existing_mount="$(findmnt -n -o TARGET --source "${data_device}" 2>/dev/null || true)"
if [[ -n "${existing_mount}" && "${existing_mount}" != "${mount_point}" ]]; then
  echo "Monitoring EBS가 예상하지 않은 경로에 마운트되어 있습니다: ${existing_mount}" >&2
  exit 1
fi

filesystem_type="$(blkid -s TYPE -o value "${data_device}" 2>/dev/null || true)"
if [[ -z "${filesystem_type}" ]]; then
  mkfs.xfs -L tikitaka-monitoring "${data_device}"
  filesystem_type='xfs'
elif [[ "${filesystem_type}" != 'xfs' ]]; then
  echo "지원하지 않는 Monitoring EBS 파일시스템입니다: ${filesystem_type}" >&2
  exit 1
fi

filesystem_uuid="$(blkid -s UUID -o value "${data_device}")"
if [[ -z "${filesystem_uuid}" ]]; then
  echo 'Monitoring EBS의 UUID를 확인할 수 없습니다.' >&2
  exit 1
fi

fstab_entry="UUID=${filesystem_uuid} ${mount_point} xfs defaults,nofail 0 2"
if ! grep -Fq "UUID=${filesystem_uuid} " /etc/fstab; then
  printf '%s\n' "${fstab_entry}" >> /etc/fstab
fi

if ! mountpoint -q "${mount_point}"; then
  mount "${mount_point}"
fi

docker --version
docker compose version
findmnt "${mount_point}"

echo 'Monitoring EC2의 Docker와 영속 데이터 볼륨 준비가 끝났습니다.'
