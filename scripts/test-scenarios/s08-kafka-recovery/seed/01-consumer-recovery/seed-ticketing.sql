-- S08 Kafka 장애·복구 부하 테스트 - Ticketing DB
-- UUID suffix의 global_index = (vu_id - 1) * 10 + iteration = 1..1000
-- 고정 테스트 UUID:
--   event_id         = 88000000-0000-0000-0000-000000000001
--   event_session_id = 87000000-0000-0000-0000-000000000001

\set ON_ERROR_STOP on
BEGIN;

WITH s08 AS (
    SELECT
        gs AS global_index,
        ((gs - 1) / 10) + 1 AS vu_id,
        ((gs - 1) % 10) + 1 AS iteration,
        9100000 + (((gs - 1) / 10) + 1) AS user_id,
        ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id,
        ('82000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS payment_id,
        ('83000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS schedule_seat_id,
        ('84000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS seat_hold_id,
        ('85000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_seat_id,
        ('86000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS venue_seat_id
    FROM generate_series(1, 1000) AS gs
)
INSERT INTO p_schedule_seat (
    schedule_seat_id, event_session_id, venue_seat_id,
    section, row_label, seat_number, seat_grade, price, seat_status,
    created_at, created_by, updated_at, updated_by
)
SELECT
    schedule_seat_id,
    '87000000-0000-0000-0000-000000000001'::uuid,
    venue_seat_id,
    'S08',
    'S08-' || lpad((((global_index - 1) / 100) + 1)::text, 2, '0'),
    (((global_index - 1) % 100) + 1)::text,
    'VIP',
    150000,
    'HELD',
    CURRENT_TIMESTAMP,
    user_id,
    CURRENT_TIMESTAMP,
    user_id
FROM s08;

WITH s08 AS (
    SELECT
        gs AS global_index,
        9100000 + (((gs - 1) / 10) + 1) AS user_id,
        ('83000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS schedule_seat_id,
        ('84000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS seat_hold_id,
        ('89000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS hold_token
    FROM generate_series(1, 1000) AS gs
)
INSERT INTO p_seat_hold (
    seat_hold_id, schedule_seat_id, user_id, hold_token,
    hold_status, held_at, expires_at, released_at, release_reason,
    created_at, created_by, updated_at, updated_by,
    deleted_at, deleted_by, idempotency_key, extended_at, reserved_at
)
SELECT
    seat_hold_id,
    schedule_seat_id,
    user_id,
    hold_token,
    'RESERVED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '2 hours',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    user_id,
    CURRENT_TIMESTAMP,
    user_id,
    NULL,
    NULL,
    'S08-SEAT-HOLD-IDEMP-' || lpad(global_index::text, 12, '0'),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM s08;

WITH s08 AS (
    SELECT
        gs AS global_index,
        9100000 + (((gs - 1) / 10) + 1) AS user_id,
        ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id,
        ('82000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS payment_id
    FROM generate_series(1, 1000) AS gs
)
INSERT INTO p_reservation (
    reservation_id, user_id, event_id, event_session_id, payment_id,
    reservation_number, event_title, session_start_at,
    seat_count, total_amount, reservation_status, failure_reason,
    payment_completed_at, idempotency_key,
    created_at, created_by, updated_at, updated_by,
    deleted_at, deleted_by, is_deleted, version
)
SELECT
    reservation_id,
    user_id,
    '88000000-0000-0000-0000-000000000001'::uuid,
    '87000000-0000-0000-0000-000000000001'::uuid,
    payment_id,
    'S08-RES-' || lpad(global_index::text, 12, '0'),
    '[S08] Kafka 복구 테스트 공연',
    CURRENT_TIMESTAMP + INTERVAL '1 day',
    1,
    150000,
    'PAYMENT_PROCESSING',
    NULL,
    NULL,
    'S08-RES-IDEMP-' || lpad(global_index::text, 12, '0'),
    CURRENT_TIMESTAMP,
    user_id,
    CURRENT_TIMESTAMP,
    user_id,
    NULL,
    NULL,
    FALSE,
    0
FROM s08;

WITH s08 AS (
    SELECT
        gs AS global_index,
        9100000 + (((gs - 1) / 10) + 1) AS user_id,
        ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id,
        ('83000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS schedule_seat_id,
        ('84000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS seat_hold_id,
        ('85000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_seat_id
    FROM generate_series(1, 1000) AS gs
)
INSERT INTO p_reservation_seats (
    reservation_seat_id, reservation_id, seat_hold_id, schedule_seat_id,
    price, created_at, created_by, updated_at, updated_by,
    deleted_at, deleted_by, is_deleted
)
SELECT
    reservation_seat_id,
    reservation_id,
    seat_hold_id,
    schedule_seat_id,
    150000,
    CURRENT_TIMESTAMP,
    user_id,
    CURRENT_TIMESTAMP,
    user_id,
    NULL,
    NULL,
    FALSE
FROM s08;

-- 초기 상태 의도:
--   S08 Reservation Inbox  = 0
--   S08 Reservation Outbox = 0
-- 본 seed는 inbox/outbox를 생성하지 않습니다.

COMMIT;
