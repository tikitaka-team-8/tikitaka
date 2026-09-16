-- S08 Kafka 장애·복구 테스트 - 공통 Payment·Notification Fixture cleanup
-- Transaction·Outbox·Notification·Inbox부터 S08 전용 ID만 정리합니다.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE s08_cleanup_reservation_ids ON COMMIT DROP AS
SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
FROM generate_series(1, 1000) AS gs;

CREATE TEMP TABLE s08_cleanup_payment_ids ON COMMIT DROP AS
SELECT ('82000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS payment_id
FROM generate_series(1, 1000) AS gs;

DELETE FROM p_notification_inbox
WHERE reservation_id IN (SELECT reservation_id FROM s08_cleanup_reservation_ids);

DELETE FROM p_notification
WHERE reservation_id IN (SELECT reservation_id FROM s08_cleanup_reservation_ids);

DELETE FROM p_payment_outbox
WHERE payment_id IN (SELECT payment_id FROM s08_cleanup_payment_ids);

DELETE FROM p_payment_transaction
WHERE payment_id IN (SELECT payment_id FROM s08_cleanup_payment_ids);

DELETE FROM p_payment
WHERE payment_id IN (SELECT payment_id FROM s08_cleanup_payment_ids)
  AND (order_id LIKE 'S08-ORDER-%' OR order_id LIKE 'S08F-ORDER-%')
  AND (idempotency_key LIKE 'S08-PAY-IDEMP-%' OR idempotency_key LIKE 'S08F-PAY-IDEMP-%');

COMMIT;
