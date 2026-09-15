#!/usr/bin/env bash

set -euo pipefail

# 실행 인자와 환경변수로 재현 가능한 실패 주입 조건을 확정합니다.
TARGET="${1:-}"
FAILURE_SEED="${FAILURE_SEED:-20260915}"
FAILURE_COUNT="${FAILURE_COUNT:-10}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-tikitaka-kafka}"
DRY_RUN="${DRY_RUN:-false}"

log_info() { printf '[INFO] %s\n' "$1" >&2; }
log_fail() { printf '[FAIL] %s\n' "$1" >&2; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    log_fail "필수 명령어 '$1'를 찾을 수 없습니다."
    exit 1
  }
}

# 대상 Consumer에 따라 Topic, 주입 시간 범위와 처리 불가능한 이벤트 유형을 선택합니다.
case "$TARGET" in
  payment)
    TOPIC="payment-events"
    DEFAULT_WINDOW_MS=5000
    EVENT_ID_PREFIX="f1000000"
    EVENT_TYPE="UNSUPPORTED_PAYMENT_EVENT"
    ;;
  reservation)
    TOPIC="reservation-events"
    DEFAULT_WINDOW_MS=12000
    EVENT_ID_PREFIX="f2000000"
    EVENT_TYPE="UNSUPPORTED_RESERVATION_EVENT"
    ;;
  *)
    log_fail "사용법: $0 <payment|reservation>"
    exit 1
    ;;
esac

INJECTION_WINDOW_MS="${INJECTION_WINDOW_MS:-$DEFAULT_WINDOW_MS}"

# Kafka에 발행하기 전에 잘못된 실행값과 컨테이너 준비 상태를 먼저 차단합니다.
if ! [[ "$FAILURE_SEED" =~ ^[0-9]+$ && "$FAILURE_COUNT" =~ ^[1-9][0-9]*$ && "$INJECTION_WINDOW_MS" =~ ^[0-9]+$ ]]; then
  log_fail "FAILURE_SEED, FAILURE_COUNT와 INJECTION_WINDOW_MS는 양의 정수여야 합니다."
  exit 1
fi
if (( INJECTION_WINDOW_MS <= 400 )); then
  log_fail "INJECTION_WINDOW_MS는 400보다 커야 합니다."
  exit 1
fi
if [[ "$DRY_RUN" != "true" && "$DRY_RUN" != "false" ]]; then
  log_fail "DRY_RUN은 true 또는 false여야 합니다."
  exit 1
fi

if [[ "$DRY_RUN" == "false" ]]; then
  require_command docker
  if ! docker ps --format '{{.Names}}' | grep --fixed-strings --line-regexp "$KAFKA_CONTAINER" >/dev/null; then
    log_fail "실행 중인 Kafka 컨테이너 '$KAFKA_CONTAINER'를 찾지 못했습니다."
    exit 1
  fi
fi

# 같은 Seed가 항상 같은 지연 목록을 만들도록 결정적 난수 생성기를 사용합니다.
random_state=$((FAILURE_SEED & 0x7fffffff))
random_value=0
delays=()

advance_random() {
  random_state=$(((1103515245 * random_state + 12345) & 0x7fffffff))
  random_value=$random_state
}

contains_delay() {
  local candidate="$1"
  local existing

  for existing in "${delays[@]:-}"; do
    if [[ "$existing" == "$candidate" ]]; then
      return 0
    fi
  done
  return 1
}

available_window_ms=$((INJECTION_WINDOW_MS - 400))
# 주입 구간 안에서 서로 겹치지 않는 실패 이벤트 발행 시각을 생성합니다.
while (( ${#delays[@]} < FAILURE_COUNT )); do
  advance_random
  candidate_delay=$((200 + (random_value % available_window_ms)))
  if ! contains_delay "$candidate_delay"; then
    delays+=("$candidate_delay")
  fi
done

# 누적 대기 시간을 계산할 수 있도록 발행 시각을 오름차순으로 정렬합니다.
sorted_delays=("${delays[@]}")
for ((i = 0; i < ${#sorted_delays[@]}; i++)); do
  for ((j = i + 1; j < ${#sorted_delays[@]}; j++)); do
    if (( sorted_delays[j] < sorted_delays[i] )); then
      temporary_delay="${sorted_delays[i]}"
      sorted_delays[i]="${sorted_delays[j]}"
      sorted_delays[j]="$temporary_delay"
    fi
  done
done

# 실제 이벤트 계약은 유지하고 eventType만 Consumer가 처리할 수 없는 값으로 구성합니다.
build_payload() {
  local failure_index="$1"
  local occurred_at="$2"
  local suffix
  local event_id
  local reservation_id
  local payment_id
  local user_id

  printf -v suffix '%012d' "$failure_index"
  event_id="${EVENT_ID_PREFIX}-0000-0000-0000-${suffix}"
  reservation_id="81000000-0000-0000-0000-${suffix}"
  payment_id="82000000-0000-0000-0000-${suffix}"
  user_id=$((9100000 + failure_index))

  if [[ "$TARGET" == "payment" ]]; then
    printf '{"eventId":"%s","eventType":"%s","occurredAt":"%s","aggregateId":"%s","version":1,"paymentId":"%s","reservationId":"%s","userId":%d,"amount":150000,"approvedAt":"%s"}' \
      "$event_id" "$EVENT_TYPE" "$occurred_at" "$reservation_id" "$payment_id" "$reservation_id" "$user_id" "$occurred_at"
  else
    printf '{"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"%s","reservationId":"%s","reservationNumber":"S08F-RES-%s","userId":%d,"eventTitle":"[S08] Kafka 실패 격리 테스트 공연","sessionStartAt":"%s"}' \
      "$event_id" "$EVENT_TYPE" "$occurred_at" "$reservation_id" "$suffix" "$user_id" "$occurred_at"
  fi
}

# 각 예정 시각까지 기다린 뒤 Kafka Key와 payload를 시간 순서대로 출력합니다.
emit_failure_events() {
  local previous_delay=0
  local failure_index=1
  local scheduled_delay
  local wait_ms
  local wait_seconds
  local occurred_at
  local reservation_suffix
  local reservation_id
  local payload

  for scheduled_delay in "${sorted_delays[@]}"; do
    wait_ms=$((scheduled_delay - previous_delay))
    printf -v wait_seconds '%d.%03d' $((wait_ms / 1000)) $((wait_ms % 1000))
    sleep "$wait_seconds"

    occurred_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    printf -v reservation_suffix '%012d' "$failure_index"
    reservation_id="81000000-0000-0000-0000-${reservation_suffix}"
    payload="$(build_payload "$failure_index" "$occurred_at")"

    log_info "target=${TARGET} index=${failure_index}/${FAILURE_COUNT} scheduledMs=${scheduled_delay} eventType=${EVENT_TYPE} eventId=${EVENT_ID_PREFIX}-0000-0000-0000-${reservation_suffix} emittedAt=${occurred_at}"
    printf '%s|%s\n' "$reservation_id" "$payload"

    previous_delay=$scheduled_delay
    failure_index=$((failure_index + 1))
  done
}

# 전후 테스트에서 같은 장애 조건을 재현할 수 있도록 Seed와 전체 주입 일정을 기록합니다.
log_info "target=${TARGET} topic=${TOPIC} seed=${FAILURE_SEED} count=${FAILURE_COUNT} windowMs=${INJECTION_WINDOW_MS} dryRun=${DRY_RUN}"
log_info "scheduledMs=$(IFS=,; printf '%s' "${sorted_delays[*]}")"

# Dry Run은 발행 계획만 검증하고, 실제 실행은 하나의 Console Producer로 순서대로 전송합니다.
if [[ "$DRY_RUN" == "true" ]]; then
  emit_failure_events >/dev/null
else
  emit_failure_events | docker exec -i "$KAFKA_CONTAINER" \
    /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic "$TOPIC" \
    --property parse.key=true \
    --property 'key.separator=|'
fi

log_info "target=${TARGET} 처리 완료: topic=${TOPIC}, failures=${FAILURE_COUNT}, dryRun=${DRY_RUN}"
