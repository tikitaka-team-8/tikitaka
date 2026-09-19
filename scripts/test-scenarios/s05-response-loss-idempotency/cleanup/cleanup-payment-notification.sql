-- S05 응답 유실 멱등성 테스트 - Payment·Notification cleanup
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE s05_cleanup_payments ON COMMIT DROP AS
SELECT payment_id, reservation_id
FROM p_payment
WHERE idempotency_key LIKE 's05-%';

DELETE FROM p_notification_inbox
WHERE reservation_id IN (SELECT reservation_id FROM s05_cleanup_payments);

DELETE FROM p_notification
WHERE reservation_id IN (SELECT reservation_id FROM s05_cleanup_payments);

DELETE FROM p_payment_outbox
WHERE payment_id IN (SELECT payment_id FROM s05_cleanup_payments);

DELETE FROM p_payment_transaction
WHERE payment_id IN (SELECT payment_id FROM s05_cleanup_payments);

DELETE FROM p_payment
WHERE payment_id IN (SELECT payment_id FROM s05_cleanup_payments)
  AND idempotency_key LIKE 's05-%';

COMMIT;
