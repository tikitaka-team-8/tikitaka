#!/usr/bin/env bash

set -euo pipefail

KAFKA_CONTAINER="${KAFKA_CONTAINER:-tikitaka-kafka}"
DRY_RUN="${DRY_RUN:-false}"
TOPIC="payment-events"
EVENT_ID="f3000000-0000-0000-0000-000000000001"
RESERVATION_ID="81000000-0000-0000-0000-000000000001"
PAYMENT_ID="82000000-0000-0000-0000-000000000001"
USER_ID=9100001

log_info() { printf '[INFO] %s\n' "$1" >&2; }
log_fail() { printf '[FAIL] %s\n' "$1" >&2; }

if [[ "$DRY_RUN" != "true" && "$DRY_RUN" != "false" ]]; then
  log_fail "DRY_RUN은 true 또는 false여야 합니다."
  exit 1
fi

occurred_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
payload="$(printf '{"eventId":"%s","eventType":"PAYMENT_SUCCEEDED","occurredAt":"%s","aggregateId":"%s","version":1,"paymentId":"%s","reservationId":"%s","userId":%d,"amount":150000,"approvedAt":"%s"}' \
  "$EVENT_ID" "$occurred_at" "$RESERVATION_ID" "$PAYMENT_ID" "$RESERVATION_ID" "$USER_ID" "$occurred_at")"

log_info "target=payment topic=${TOPIC} eventId=${EVENT_ID} reservationId=${RESERVATION_ID} emittedAt=${occurred_at} dryRun=${DRY_RUN}"

if [[ "$DRY_RUN" == "true" ]]; then
  printf '%s|%s\n' "$RESERVATION_ID" "$payload"
  exit 0
fi

command -v docker >/dev/null 2>&1 || {
  log_fail "필수 명령어 'docker'를 찾을 수 없습니다."
  exit 1
}

if ! docker ps --format '{{.Names}}' | grep --fixed-strings --line-regexp "$KAFKA_CONTAINER" >/dev/null; then
  log_fail "실행 중인 Kafka 컨테이너 '$KAFKA_CONTAINER'를 찾지 못했습니다."
  exit 1
fi

# Git Bash가 컨테이너의 /opt 경로를 Windows 경로로 바꾸지 않도록 변환을 끕니다.
printf '%s|%s\n' "$RESERVATION_ID" "$payload" | MSYS_NO_PATHCONV=1 docker exec -i "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$TOPIC" \
  --property parse.key=true \
  --property 'key.separator=|'

log_info "Retry 성공 보충 이벤트 발행 완료"
