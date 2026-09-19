-- S05 예매 생성 응답 유실 - Payment 상태 검증
\set ON_ERROR_STOP on
\set idempotency_key 's05-reservation-response-loss-01'

\pset pager off
\x on

SELECT
    COUNT(*) AS payment_total,
    COUNT(DISTINCT payment_id) AS unique_payment_count,
    COUNT(DISTINCT reservation_id) AS unique_reservation_link_count,
    COUNT(*) FILTER (WHERE status = 'READY') AS payment_ready_count,
    MIN(payment_id::text) AS payment_id,
    MIN(reservation_id::text) AS reservation_id,
    MIN(amount) AS amount
FROM p_payment
WHERE idempotency_key = :'idempotency_key';

SELECT
    COUNT(*) AS payment_transaction_total,
    COUNT(DISTINCT transaction_id) AS unique_transaction_count
FROM p_payment_transaction
WHERE payment_id IN (
    SELECT payment_id
    FROM p_payment
    WHERE idempotency_key = :'idempotency_key'
);

SELECT
    COUNT(*) AS payment_outbox_total,
    COUNT(DISTINCT outbox_id) AS unique_outbox_count
FROM p_payment_outbox
WHERE payment_id IN (
    SELECT payment_id
    FROM p_payment
    WHERE idempotency_key = :'idempotency_key'
);
