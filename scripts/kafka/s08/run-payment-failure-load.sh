#!/usr/bin/env bash

set -euo pipefail

# 호출 위치와 관계없이 저장소 루트와 두 테스트 스크립트의 절대 경로를 계산합니다.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"
INJECTOR_PID=""

# k6가 실패하거나 사용자가 중단해도 백그라운드 실패 주입 프로세스가 남지 않게 정리합니다.
cleanup() {
  if [[ -n "$INJECTOR_PID" ]] && kill -0 "$INJECTOR_PID" >/dev/null 2>&1; then
    kill "$INJECTOR_PID" >/dev/null 2>&1 || true
    wait "$INJECTOR_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT INT TERM

# 실제 부하를 시작하기 전에 k6 실행 파일이 설치되어 있는지 확인합니다.
command -v k6 >/dev/null 2>&1 || {
  printf '[FAIL] 필수 명령어 k6를 찾을 수 없습니다.\n' >&2
  exit 1
}

# 정상 결제 승인 부하와 처리 불가능한 payment-events 10건을 같은 시간대에 발생시킵니다.
printf '[INFO] Payment 실패 이벤트 주입과 k6 결제 승인 부하를 동시에 시작합니다.\n'
"${SCRIPT_DIR}/inject-failure-events.sh" payment &
INJECTOR_PID=$!

k6 run "${REPOSITORY_ROOT}/scripts/k6/kafka-failure-recovery.js"

# k6 종료 뒤에도 실패 이벤트 주입이 모두 끝날 때까지 기다려 테스트 입력을 완성합니다.
wait "$INJECTOR_PID"
INJECTOR_PID=""

printf '[INFO] k6 정상 요청과 Payment 실패 이벤트 주입을 완료했습니다.\n'
