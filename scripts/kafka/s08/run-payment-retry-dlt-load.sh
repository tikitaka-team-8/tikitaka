#!/usr/bin/env bash

set -euo pipefail

# 호출 위치와 관계없이 저장소 루트와 테스트 스크립트의 절대 경로를 계산합니다.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"
INJECTOR_PID=""

# k6가 실패하거나 사용자가 중단해도 백그라운드 혼합 실패 주입 프로세스를 정리합니다.
cleanup() {
  if [[ -n "$INJECTOR_PID" ]] && kill -0 "$INJECTOR_PID" >/dev/null 2>&1; then
    kill "$INJECTOR_PID" >/dev/null 2>&1 || true
    wait "$INJECTOR_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT INT TERM

# 정상 결제 승인 부하에 비재시도 5건과 재시도 5건을 같은 시간대에 주입합니다.
printf '[INFO] Payment Retry·DLT 혼합 실패 이벤트와 k6 결제 승인 부하를 동시에 시작합니다.\n'
FAILURE_PROFILE=mixed "${SCRIPT_DIR}/inject-failure-events.sh" payment &
INJECTOR_PID=$!

k6 run "${REPOSITORY_ROOT}/scripts/k6/kafka-failure-recovery.js"

# k6가 먼저 끝나더라도 혼합 실패 이벤트 10건이 모두 발행될 때까지 기다립니다.
wait "$INJECTOR_PID"
INJECTOR_PID=""

printf '[INFO] k6 정상 요청과 Payment Retry·DLT 혼합 실패 주입을 완료했습니다.\n'
