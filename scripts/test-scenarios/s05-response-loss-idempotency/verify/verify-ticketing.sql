-- S05 예매 생성 응답 유실 - Ticketing 상태 검증
\set ON_ERROR_STOP on
\set idempotency_key 's05-reservation-response-loss-01'
\set seat_hold_id '52050000-0000-0000-0000-000000000001'

\pset pager off
\x on

SELECT
    COUNT(*) AS reservation_total,
    COUNT(DISTINCT reservation_id) AS unique_reservation_count,
    COUNT(DISTINCT payment_id) FILTER (WHERE payment_id IS NOT NULL) AS unique_payment_link_count,
    COUNT(*) FILTER (WHERE reservation_status = 'PAYMENT_PROCESSING') AS payment_processing_count,
    MIN(reservation_id::text) AS reservation_id,
    MIN(payment_id::text) AS payment_id,
    MIN(reservation_number) AS reservation_number
FROM p_reservation
WHERE idempotency_key = :'idempotency_key'
  AND is_deleted = FALSE;

SELECT
    COUNT(*) AS reservation_seat_total,
    COUNT(DISTINCT rs.seat_hold_id) AS unique_seat_hold_count,
    COUNT(DISTINCT rs.schedule_seat_id) AS unique_schedule_seat_count
FROM p_reservation_seats rs
JOIN p_reservation r ON r.reservation_id = rs.reservation_id
WHERE r.idempotency_key = :'idempotency_key'
  AND r.is_deleted = FALSE
  AND rs.is_deleted = FALSE;

SELECT
    COUNT(*) AS seat_hold_total,
    MIN(user_id) AS owner_user_id,
    MIN(hold_status) AS hold_status,
    MIN(reserved_at) AS reserved_at,
    MIN(expires_at) AS expires_at
FROM p_seat_hold
WHERE seat_hold_id = :'seat_hold_id'::uuid
  AND deleted_at IS NULL;

SELECT
    COUNT(*) AS reservation_outbox_total,
    COUNT(DISTINCT event_id) AS unique_outbox_event_count
FROM p_reservation_outbox
WHERE reservation_id IN (
    SELECT reservation_id
    FROM p_reservation
    WHERE idempotency_key = :'idempotency_key'
);
