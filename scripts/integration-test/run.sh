#!/usr/bin/env bash

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
EVENT_ID="${EVENT_ID:-30000000-0000-0000-0000-000000000001}"
SESSION_ID="${SESSION_ID:-31000000-0000-0000-0000-000000000001}"
EXPECTED_PRICE="${EXPECTED_PRICE:-150000}"
ADMISSION_POLL_MAX_ATTEMPTS="${ADMISSION_POLL_MAX_ATTEMPTS:-20}"
ADMISSION_POLL_INTERVAL_SECONDS="${ADMISSION_POLL_INTERVAL_SECONDS:-1}"
HTTP_CONNECT_TIMEOUT_SECONDS="${HTTP_CONNECT_TIMEOUT_SECONDS:-5}"
HTTP_MAX_TIME_SECONDS="${HTTP_MAX_TIME_SECONDS:-15}"

RUN_SUFFIX="${TEST_RUN_ID:-$(date +%s)-$$-${RANDOM}}"
RUN_SUFFIX="${RUN_SUFFIX:0:24}"
TEST_EMAIL="ci.${RUN_SUFFIX}@test.tikitaka.local"
TEST_PASSWORD="Tikitaka!Aa1-${RUN_SUFFIX:0:16}"
TEST_NAME="CI 테스트 사용자"
TEST_NICKNAME="ci-${RUN_SUFFIX}"

HTTP_STATUS=""
HTTP_BODY=""
EXTRA_HEADERS=()

log_step() { printf '\n[%s] %s\n' "$1" "$2"; }
log_ok() { printf '[OK] %s\n' "$1"; }
log_fail() { printf '[FAIL] %s\n' "$1" >&2; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    log_fail "필수 명령어 '$1'를 찾을 수 없습니다."
    exit 1
  }
}

http() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local curl_args=(
    --silent
    --show-error
    --connect-timeout "$HTTP_CONNECT_TIMEOUT_SECONDS"
    --max-time "$HTTP_MAX_TIME_SECONDS"
    --write-out $'\n%{http_code}'
    --request "$method"
    "${GATEWAY_URL}${path}"
    --header "Content-Type: application/json"
  )

  if [[ -n "$data" ]]; then
    curl_args+=(--data "$data")
  fi
  if [[ ${#EXTRA_HEADERS[@]} -gt 0 ]]; then
    curl_args+=("${EXTRA_HEADERS[@]}")
  fi

  local response
  response="$(curl "${curl_args[@]}")"
  HTTP_STATUS="${response##*$'\n'}"
  HTTP_BODY="${response%$'\n'*}"
}

assert_status() {
  local expected="$1"
  local step="$2"

  if [[ "$HTTP_STATUS" != "$expected" ]]; then
    log_fail "${step}: HTTP ${expected} 기대, 실제 ${HTTP_STATUS}"
    printf '응답 본문: %s\n' "$HTTP_BODY" >&2
    exit 1
  fi

  log_ok "${step} (HTTP ${HTTP_STATUS})"
}

assert_json() {
  local expression="$1"
  local step="$2"

  if ! jq --exit-status "$expression" >/dev/null <<<"$HTTP_BODY"; then
    log_fail "${step}: 응답의 핵심 데이터가 기대와 다릅니다."
    printf '응답 본문: %s\n' "$HTTP_BODY" >&2
    exit 1
  fi

  log_ok "$step"
}

require_command curl
require_command jq

log_step "HAPPY_SIGNUP" "회원가입"
EXTRA_HEADERS=()
http POST "/api/v1/auth/signup" "$(jq --null-input \
  --arg email "$TEST_EMAIL" \
  --arg password "$TEST_PASSWORD" \
  --arg name "$TEST_NAME" \
  --arg nickname "$TEST_NICKNAME" \
  '{email:$email, password:$password, name:$name, nickname:$nickname}')"
assert_status 201 "회원가입"
USER_ID="$(jq --raw-output '.data.userId // empty' <<<"$HTTP_BODY")"
if [[ -z "$USER_ID" ]]; then
  log_fail "회원가입 응답에서 userId를 찾지 못했습니다."
  exit 1
fi

log_step "HAPPY_LOGIN" "로그인과 Access Token 저장"
http POST "/api/v1/auth/login" "$(jq --null-input \
  --arg email "$TEST_EMAIL" \
  --arg password "$TEST_PASSWORD" \
  '{email:$email, password:$password}')"
assert_status 200 "로그인"
ACCESS_TOKEN="$(jq --raw-output '.data.accessToken // empty' <<<"$HTTP_BODY")"
if [[ -z "$ACCESS_TOKEN" ]]; then
  log_fail "로그인 응답에서 accessToken을 찾지 못했습니다."
  exit 1
fi

log_step "SECURITY_MISSING_TOKEN" "보호 API의 토큰 누락 차단"
EXTRA_HEADERS=()
http GET "/api/v1/event-sessions/${SESSION_ID}/queue/me"
assert_status 401 "토큰 누락 차단"

log_step "SECURITY_INVALID_JWT" "유효하지 않은 JWT 차단"
EXTRA_HEADERS=(--header "Authorization: Bearer invalid-jwt")
http GET "/api/v1/event-sessions/${SESSION_ID}/queue/me"
assert_status 401 "유효하지 않은 JWT 차단"

log_step "SECURITY_INTERNAL_PATH" "Gateway의 내부 API 외부 접근 차단"
EXTRA_HEADERS=(
  --header "Authorization: Bearer ${ACCESS_TOKEN}"
  --header "X-Service-Key: forged-service-key"
)
http GET "/api/v1/internal/event-sessions/${SESSION_ID}/sales-status"
assert_status 403 "내부 API 외부 접근 차단"

log_step "HAPPY_EVENT_LIST" "공개 공연 목록 조회"
EXTRA_HEADERS=(--header "Authorization: Bearer ${ACCESS_TOKEN}")
http GET "/api/v1/events"
assert_status 200 "공연 목록 조회"
if ! jq --exit-status --arg eventId "$EVENT_ID" \
  '.data[]? | select(.eventId == $eventId)' >/dev/null <<<"$HTTP_BODY"; then
  log_fail "공연 목록에서 테스트 공연을 찾지 못했습니다."
  exit 1
fi
log_ok "테스트 공연 포함 확인"

log_step "HAPPY_EVENT_DETAIL" "공개 공연 상세 조회"
http GET "/api/v1/events/${EVENT_ID}"
assert_status 200 "공연 상세 조회"
assert_json ".data.eventId == \"${EVENT_ID}\"" "공연 ID 확인"

log_step "HAPPY_SESSION_AND_PRICE" "공연 회차와 가격 조회"
http GET "/api/v1/events/${EVENT_ID}/sessions/${SESSION_ID}"
assert_status 200 "공연 회차 조회"
assert_json ".data.sessionId == \"${SESSION_ID}\"" "회차 ID 확인"
assert_json ".data.sectionPrices | any(.priceAmount == ${EXPECTED_PRICE})" "회차 가격 확인"

log_step "HAPPY_QUEUE_ENTER" "위조 사용자 헤더를 포함한 대기열 진입"
EXTRA_HEADERS=(
  --header "Authorization: Bearer ${ACCESS_TOKEN}"
  --header "X-User-Id: 999999999"
  --header "X-User-Role: ADMIN"
)
http POST "/api/v1/event-sessions/${SESSION_ID}/queue"
assert_status 200 "대기열 진입"
if ! jq --exit-status --arg userId "$USER_ID" \
  '.data.userId | tostring == $userId' >/dev/null <<<"$HTTP_BODY"; then
  log_fail "Gateway가 위조 사용자 ID를 제거하지 않았거나 인증 사용자 ID를 전달하지 못했습니다."
  exit 1
fi
log_ok "JWT 사용자 ID 전달과 위조 헤더 제거 확인"

log_step "HAPPY_QUEUE_ADMISSION" "대기열 입장 승인 폴링"
EXTRA_HEADERS=(--header "Authorization: Bearer ${ACCESS_TOKEN}")
ADMISSION_TOKEN=""
QUEUE_STATUS="UNKNOWN"
for ((attempt = 1; attempt <= ADMISSION_POLL_MAX_ATTEMPTS; attempt++)); do
  http GET "/api/v1/event-sessions/${SESSION_ID}/queue/me"
  assert_status 200 "대기열 상태 조회 ${attempt}/${ADMISSION_POLL_MAX_ATTEMPTS}"
  QUEUE_STATUS="$(jq --raw-output '.data.status // empty' <<<"$HTTP_BODY")"

  if [[ "$QUEUE_STATUS" == "ADMITTED" ]]; then
    ADMISSION_TOKEN="$(jq --raw-output '.data.admissionToken // empty' <<<"$HTTP_BODY")"
    break
  fi

  sleep "$ADMISSION_POLL_INTERVAL_SECONDS"
done

if [[ -z "$ADMISSION_TOKEN" ]]; then
  log_fail "제한 시간 안에 대기열 승인을 받지 못했습니다. 마지막 상태: ${QUEUE_STATUS}"
  exit 1
fi
log_ok "입장 토큰 발급 확인"

log_step "HAPPY_SEAT_LIST" "좌석 목록 조회"
EXTRA_HEADERS=(
  --header "Authorization: Bearer ${ACCESS_TOKEN}"
  --header "X-Queue-Token: ${ADMISSION_TOKEN}"
)
http GET "/api/v1/schedules/${SESSION_ID}/seats"
assert_status 200 "좌석 목록 조회"
SEAT_ID="$(jq --raw-output '.data.seats[0].scheduleSeatId // empty' <<<"$HTTP_BODY")"
if [[ -z "$SEAT_ID" ]]; then
  log_fail "좌석 목록에서 scheduleSeatId를 찾지 못했습니다."
  exit 1
fi
log_ok "조회 가능한 좌석 확인"

log_step "HAPPY_SEAT_DETAIL" "좌석 상세 조회"
http GET "/api/v1/schedules/${SESSION_ID}/seats/${SEAT_ID}"
assert_status 200 "좌석 상세 조회"
assert_json ".data.scheduleSeatId == \"${SEAT_ID}\"" "좌석 상세 ID 확인"

printf '\n[OK] 1차 통합 테스트 Happy Path와 Gateway 보안 시나리오가 모두 통과했습니다.\n'
