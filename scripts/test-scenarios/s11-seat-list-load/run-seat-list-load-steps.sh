#!/usr/bin/env bash
# 좌석 목록 반복조회 - 50 -> 100 -> 300 -> 500 -> 1,000 VU 단계별 부하테스트
#
# 큐 토큰 발급은 이제 seat-list-load.js의 setup() 단계에서 k6가 직접 처리합니다
# (http.batch()로 병렬 enterQueue -> queue/me 폴링). python/pip/venv 불필요, k6만 있으면 됩니다.
#
# ⚠️ admission-token-ttl(기본 3분=180s)보다 한 단계(mint + ramp + 유지 + ramp-down)가 길면
#    막판 요청이 토큰 만료(Q-001)로 실패합니다 - 실측으로 확인됨(1000 VU에서 mint 20~21s +
#    기본 ramp 30s+유지 2m+ramp-down 30s = 200s로 TTL을 넘겨 후반부 요청이 대량 실패했음).
#    그래서 기본값을 mint 시간(최대 VU 기준 약 25~30s 가정)을 더해도 TTL(180s)보다
#    확실히 짧게(합계 100s) 낮춰뒀습니다. 그래도 부족하면 테스트 동안만
#    ticketing-service의 queue.admission-token-ttl을 PT30M 정도로 늘려서 재기동하세요.
#
# 사용법:
#   BASE_URL=http://localhost:8082 ./run-seat-list-load-steps.sh
#
# 실행 위치: scripts/test-scenarios/s11-seat-list-load/ (원래 k6/ 아래 평면 구조였던 것을
#   "테스트 파일 관리 변경 사항" 컨벤션에 맞춰 이 폴더로 옮겼습니다).
# 결과 파일(JSON)은 이 폴더가 아니라 artifacts/k6/s11-seat-list-load/<타임스탬프>/ 에 쌓입니다
#   (원본 k6 JSON·로그는 Git에 올리지 않는 파일이라 artifacts/ 아래에 둡니다).

set -euo pipefail
cd "$(dirname "$0")"

BASE_URL="${BASE_URL:-http://localhost:8082}"
SESSION_ID="${SESSION_ID:-31000000-0000-0000-0000-000000000001}"
STAGES=(50 100 300 500 1000)
LOAD_RAMP="${LOAD_RAMP:-20s}"
LOAD_DURATION="${LOAD_DURATION:-1m}"
RECOVERY_WAIT="${RECOVERY_WAIT:-30}"
TS="$(date +%Y%m%d_%H%M%S)"
RESULT_DIR="../../../artifacts/k6/s11-seat-list-load/${TS}"
PG_CONN='psql "host=localhost port=5434 dbname=tikitaka_ticketing user=ticketing" -tA'

mkdir -p "$RESULT_DIR"
echo "결과: ${RESULT_DIR}"

echo
echo "================================================"
echo " 0) JVM/커넥션 풀 워밍업 (측정에 포함하지 않음, 20 VU)"
echo "================================================"
k6 run \
  --env SCENARIO=compare --env COMPARE_VUS=20 --env COMPARE_DURATION=1m \
  --env BASE_URL="$BASE_URL" --env SESSION_ID="$SESSION_ID" \
  seat-list-load.js

for VUS in "${STAGES[@]}"; do
  echo
  echo "================================================"
  echo " ${VUS} VU 단계 시작 (ramp ${LOAD_RAMP} / 유지 ${LOAD_DURATION} / ramp-down ${LOAD_RAMP})"
  echo "================================================"

  # threshold 초과(p95/오류율)는 이번 테스트에서 "실패"가 아니라 관찰 대상입니다.
  # k6가 non-zero exit code를 내도 스크립트가 죽지 않고 다음 VU 단계로 계속 진행하도록 합니다.
  if ! k6 run \
    --env SCENARIO=load --env LOAD_VUS="$VUS" \
    --env LOAD_RAMP="$LOAD_RAMP" --env LOAD_DURATION="$LOAD_DURATION" \
    --env BASE_URL="$BASE_URL" --env SESSION_ID="$SESSION_ID" \
    --summary-export="${RESULT_DIR}/${VUS}vu.json" \
    seat-list-load.js; then
    echo "!! ${VUS} VU 단계에서 threshold 초과 (계속 진행, 결과는 ${RESULT_DIR}/${VUS}vu.json 확인)"
  fi

  echo "-- ${VUS} VU 단계 종료. 시스템 정상화 대기 ${RECOVERY_WAIT}s --"
  sleep "$RECOVERY_WAIT"

  ACTIVE=$(eval $PG_CONN -c \
    "\"SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'active';\"" \
    2>/dev/null || echo "확인불가(psql 접속 실패 - 직접 Grafana/DB에서 확인하세요)")
  echo "-- 현재 활성 DB 커넥션 수: ${ACTIVE} (평시 수준으로 내려왔는지 확인 후 다음 단계로) --"
done

echo
echo "모든 단계 완료. 결과: ${RESULT_DIR}/*.json"
