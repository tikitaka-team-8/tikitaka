-- S08 Kafka 실패 격리 테스트 - Payment & Notification DB
-- 사용자 600명의 결제 1건 또는 2건, 총 1,000건을 READY 상태로 준비합니다.

\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE s08_failure_fixture ON COMMIT DROP AS
WITH indexed AS (
    SELECT
        gs AS global_index,
        ((gs - 1) / 10) + 1 AS vu_id,
        ((gs - 1) % 10) + 1 AS approval_position
    FROM generate_series(1, 1000) AS gs
), mapped AS (
    SELECT
        global_index,
        vu_id,
        CASE
            WHEN approval_position <= 8 THEN ((approval_position - 1) / 2) + 1
            ELSE approval_position - 4
        END AS user_iteration
    FROM indexed
)
SELECT
    global_index,
    9100000 + ((vu_id - 1) * 6) + user_iteration AS user_id,
    ('81000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS reservation_id,
    ('82000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS payment_id
FROM mapped;

INSERT INTO p_payment (
    payment_id, reservation_id, user_id, order_id, idempotency_key,
    amount, currency, payment_method, payment_provider,
    pg_payment_key, status, failure_code, failure_reason,
    requested_at, approved_at, canceled_at, created_at, updated_at
)
SELECT
    payment_id,
    reservation_id,
    user_id,
    'S08F-ORDER-' || lpad(global_index::text, 12, '0'),
    'S08F-PAY-IDEMP-' || lpad(global_index::text, 12, '0'),
    150000,
    'KRW',
    NULL,
    'MOCK',
    NULL,
    'READY',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM s08_failure_fixture;

-- Payment Transaction, Outbox, Notification과 Notification Inbox는 0건이어야 합니다.
COMMIT;
