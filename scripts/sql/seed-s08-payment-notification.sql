-- S08 Kafka 장애·복구 부하 테스트 - Payment & Notification DB
-- 초기 Payment는 전부 READY이며 승인 API 호출 시 CSV의 payment_method를 사용합니다.

\set ON_ERROR_STOP on
BEGIN;

WITH s08 AS (
    SELECT
        gs AS global_index,
        9100000 + (((gs - 1) / 10) + 1) AS user_id,
        ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id,
        ('82000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS payment_id
    FROM generate_series(1, 1000) AS gs
)
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
    'S08-ORDER-' || lpad(global_index::text, 12, '0'),
    'S08-PAY-IDEMP-' || lpad(global_index::text, 12, '0'),
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
FROM s08;

-- 초기 상태 의도:
--   S08 Payment Transaction = 0
--   S08 Payment Outbox      = 0
--   S08 Notification        = 0
--   S08 Notification Inbox  = 0
-- 본 seed는 위 테이블에 레코드를 생성하지 않습니다.

COMMIT;
