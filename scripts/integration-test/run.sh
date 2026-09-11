#!/usr/bin/env bash

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
EVENT_ID="${EVENT_ID:-30000000-0000-0000-0000-000000000001}"
SESSION_ID="${SESSION_ID:-31000000-0000-0000-0000-000000000001}"
EXPECTED_PRICE="${EXPECTED_PRICE:-150000}"
ADMISSION_POLL_MAX_ATTEMPTS="${ADMISSION_POLL_MAX_ATTEMPTS:-20}"
ADMISSION_POLL_INTERVAL_SECONDS="${ADMISSION_POLL_INTERVAL_SECONDS:-1}"
RESERVATION_POLL_MAX_ATTEMPTS="${RESERVATION_POLL_MAX_ATTEMPTS:-30}"
RESERVATION_POLL_INTERVAL_SECONDS="${RESERVATION_POLL_INTERVAL_SECONDS:-1}"
NOTIFICATION_POLL_MAX_ATTEMPTS="${NOTIFICATION_POLL_MAX_ATTEMPTS:-30}"
NOTIFICATION_POLL_INTERVAL_SECONDS="${NOTIFICATION_POLL_INTERVAL_SECONDS:-1}"
HTTP_CONNECT_TIMEOUT_SECONDS="${HTTP_CONNECT_TIMEOUT_SECONDS:-5}"
HTTP_MAX_TIME_SECONDS="${HTTP_MAX_TIME_SECONDS:-15}"

RUN_SUFFIX="${TEST_RUN_ID:-$(date +%s)-$$-${RANDOM}}"
RUN_SUFFIX="${RUN_SUFFIX:0:24}"
TEST_EMAIL="ci.${RUN_SUFFIX}@test.tikitaka.local"
TEST_PASSWORD="Tikitaka!Aa1-${RUN_SUFFIX:0:16}"
TEST_NAME="CI 테스트 사용자"
TEST_NICKNAME="ci-${RUN_SUFFIX}"
SEAT_HOLD_IDEMPOTENCY_KEY="seat-hold-${RUN_SUFFIX}"
RESERVATION_IDEMPOTENCY_KEY="reservation-${RUN_SUFFIX}"

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
SEAT_ID="$(jq --raw-output --argjson expectedPrice "$EXPECTED_PRICE" \
  'first(.data.seats[]? | select(.price == $expectedPrice and .seatStatus == "AVAILABLE") | .scheduleSeatId) // empty' \
  <<<"$HTTP_BODY")"
if [[ -z "$SEAT_ID" ]]; then
  log_fail "좌석 목록에서 가격 ${EXPECTED_PRICE}원의 예매 가능한 좌석을 찾지 못했습니다."
  exit 1
fi
log_ok "가격과 상태가 일치하는 예매 가능 좌석 확인"

log_step "HAPPY_SEAT_DETAIL" "좌석 상세 조회"
http GET "/api/v1/schedules/${SESSION_ID}/seats/${SEAT_ID}"
assert_status 200 "좌석 상세 조회"
assert_json ".data.scheduleSeatId == \"${SEAT_ID}\"" "좌석 상세 ID 확인"
assert_json ".data.price == ${EXPECTED_PRICE} and .data.seatStatus == \"AVAILABLE\"" "좌석 상세 가격과 상태 확인"

log_step "HAPPY_SEAT_HOLD" "좌석 선점"
EXTRA_HEADERS=(
  --header "Authorization: Bearer ${ACCESS_TOKEN}"
  --header "Idempotency-Key: ${SEAT_HOLD_IDEMPOTENCY_KEY}"
)
http POST "/api/v1/schedules/${SESSION_ID}/seats/${SEAT_ID}/hold"
assert_status 200 "좌석 선점"
SEAT_HOLD_ID="$(jq --raw-output '.data.seatHoldId // empty' <<<"$HTTP_BODY")"
if [[ -z "$SEAT_HOLD_ID" ]]; then
  log_fail "좌석 선점 응답에서 seatHoldId를 찾지 못했습니다."
  exit 1
fi
assert_json '.data.holdToken != null and .data.expiresAt != null' "선점 토큰과 만료 시각 확인"

log_step "HAPPY_RESERVATION_CREATE" "예매 생성과 Payment READY 생성"
EXTRA_HEADERS=(
  --header "Authorization: Bearer ${ACCESS_TOKEN}"
  --header "Idempotency-Key: ${RESERVATION_IDEMPOTENCY_KEY}"
)
http POST "/api/v1/reservations" "$(jq --null-input \
  --arg seatHoldId "$SEAT_HOLD_ID" \
  '{seatHoldIds:[$seatHoldId]}')"
assert_status 201 "예매 생성"
RESERVATION_ID="$(jq --raw-output '.data.reservationId // empty' <<<"$HTTP_BODY")"
RESERVATION_NUMBER="$(jq --raw-output '.data.reservationNumber // empty' <<<"$HTTP_BODY")"
PAYMENT_ID="$(jq --raw-output '.data.paymentId // empty' <<<"$HTTP_BODY")"
if [[ -z "$RESERVATION_ID" || -z "$RESERVATION_NUMBER" || -z "$PAYMENT_ID" ]]; then
  log_fail "예매 생성 응답에서 reservationId, reservationNumber 또는 paymentId를 찾지 못했습니다."
  exit 1
fi
assert_json ".data.reservationStatus == \"PAYMENT_PROCESSING\"" "예매 결제 처리 상태 확인"
assert_json ".data.seatCount == 1 and .data.totalAmount == ${EXPECTED_PRICE}" "예매 좌석 수와 금액 확인"

log_step "HAPPY_PAYMENT_READY" "생성된 결제 조회"
EXTRA_HEADERS=(--header "Authorization: Bearer ${ACCESS_TOKEN}")
http GET "/api/v1/payments/${PAYMENT_ID}"
assert_status 200 "결제 조회"
assert_json ".data.paymentId == \"${PAYMENT_ID}\" and .data.reservationId == \"${RESERVATION_ID}\"" "결제와 예매 ID 연결 확인"
assert_json ".data.status == \"READY\" and .data.amount == ${EXPECTED_PRICE}" "결제 READY 상태와 금액 확인"

log_step "HAPPY_PAYMENT_APPROVE" "결제 승인과 성공 Outbox 생성"
http POST "/api/v1/payments/${PAYMENT_ID}/approve" '{"paymentMethod":"CARD"}'
assert_status 200 "결제 승인"
assert_json ".data.paymentId == \"${PAYMENT_ID}\" and .data.status == \"APPROVED\"" "결제 승인 상태 확인"

log_step "HAPPY_RESERVATION_CONFIRMED" "Kafka 결제 성공 이벤트 반영 폴링"
RESERVATION_STATUS="UNKNOWN"
for ((attempt = 1; attempt <= RESERVATION_POLL_MAX_ATTEMPTS; attempt++)); do
  http GET "/api/v1/reservations/${RESERVATION_ID}"
  assert_status 200 "예매 상태 조회 ${attempt}/${RESERVATION_POLL_MAX_ATTEMPTS}"
  RESERVATION_STATUS="$(jq --raw-output '.data.reservationStatus // empty' <<<"$HTTP_BODY")"

  if [[ "$RESERVATION_STATUS" == "CONFIRMED" ]]; then
    break
  fi
  if [[ "$RESERVATION_STATUS" == "FAILED" || "$RESERVATION_STATUS" == "CANCELLED" ]]; then
    log_fail "결제 성공 이벤트 대기 중 예매가 실패 상태로 변경됐습니다: ${RESERVATION_STATUS}"
    exit 1
  fi

  sleep "$RESERVATION_POLL_INTERVAL_SECONDS"
done

if [[ "$RESERVATION_STATUS" != "CONFIRMED" ]]; then
  log_fail "제한 시간 안에 결제 성공 이벤트가 반영되지 않았습니다. 마지막 예매 상태: ${RESERVATION_STATUS}"
  exit 1
fi
assert_json ".data.reservationId == \"${RESERVATION_ID}\" and .data.paymentCompletedAt != null" "예매 확정과 결제 완료 시각 확인"

log_step "HAPPY_NOTIFICATION_CREATED" "예매 확정 알림 생성 폴링"
NOTIFICATION_ID=""
NOTIFICATION_COUNT=0
for ((attempt = 1; attempt <= NOTIFICATION_POLL_MAX_ATTEMPTS; attempt++)); do
  http GET "/api/v1/notifications?notificationType=RESERVATION_CONFIRMED&readStatus=UNREAD&size=50"
  assert_status 200 "읽지 않은 예매 확정 알림 조회 ${attempt}/${NOTIFICATION_POLL_MAX_ATTEMPTS}"
  NOTIFICATION_COUNT="$(jq --raw-output '.meta.totalElements // (.data | length) // 0' <<<"$HTTP_BODY")"
  NOTIFICATION_ID="$(jq --raw-output \
    --arg userId "$USER_ID" \
    --arg reservationNumber "$RESERVATION_NUMBER" \
    'first(.data[]? | select(
      (.userId | tostring) == $userId
      and .notificationType == "RESERVATION_CONFIRMED"
      and .readStatus == "UNREAD"
      and (.content | contains($reservationNumber))
    ) | .notificationId) // empty' <<<"$HTTP_BODY")"

  if [[ -n "$NOTIFICATION_ID" ]]; then
    break
  fi

  sleep "$NOTIFICATION_POLL_INTERVAL_SECONDS"
done

if [[ -z "$NOTIFICATION_ID" ]]; then
  log_fail "제한 시간 안에 예매 확정 알림이 생성되지 않았습니다. 예매번호: ${RESERVATION_NUMBER}, 마지막 조회 건수: ${NOTIFICATION_COUNT}"
  exit 1
fi
log_ok "현재 예매번호와 연결된 읽지 않은 알림 확인"

log_step "HAPPY_NOTIFICATION_READ" "알림 상세 조회와 읽음 처리"
http PATCH "/api/v1/notifications/${NOTIFICATION_ID}/read"
assert_status 200 "알림 상세 조회와 읽음 처리"
assert_json ".data.notificationId == \"${NOTIFICATION_ID}\"" "알림 ID 확인"
assert_json ".data.userId | tostring == \"${USER_ID}\"" "알림 사용자 ID 확인"
assert_json ".data.reservationId == \"${RESERVATION_ID}\" and .data.reservationNumber == \"${RESERVATION_NUMBER}\"" "알림과 예매 연결 확인"
assert_json '.data.notificationType == "RESERVATION_CONFIRMED" and .data.readStatus == "READ" and .data.lastViewedAt != null' "알림 읽음 상태와 마지막 조회 시각 확인"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    printf 'reservation_id=%s\n' "$RESERVATION_ID"
    printf 'reservation_number=%s\n' "$RESERVATION_NUMBER"
    printf 'seat_hold_id=%s\n' "$SEAT_HOLD_ID"
    printf 'schedule_seat_id=%s\n' "$SEAT_ID"
    printf 'payment_id=%s\n' "$PAYMENT_ID"
    printf 'notification_id=%s\n' "$NOTIFICATION_ID"
  } >>"$GITHUB_OUTPUT"
fi

printf '\n[OK] 결제, 예매 확정, 알림 조회와 읽음 처리까지의 Happy Path 및 Gateway 보안 시나리오가 모두 통과했습니다.\n'
