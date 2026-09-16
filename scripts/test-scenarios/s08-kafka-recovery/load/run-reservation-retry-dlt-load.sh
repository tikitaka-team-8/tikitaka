#!/usr/bin/env bash

set -euo pipefail

# Ticketing Consumer가 정상 Reservation 이벤트를 발행하는 동안 별도 터미널에서 실행합니다.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# 비재시도 5건과 재시도 대상 5건을 reservation-events에 고정 Seed 순서로 주입합니다.
printf '[INFO] Reservation Retry·DLT 혼합 실패 이벤트 주입을 시작합니다.\n'
FAILURE_PROFILE=mixed "${SCRIPT_DIR}/../inject/inject-failure-events.sh" reservation
printf '[INFO] Reservation Retry·DLT 혼합 실패 이벤트 10건 주입을 완료했습니다.\n'
