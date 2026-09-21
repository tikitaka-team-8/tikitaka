-- S08 Kafka 실패 격리 테스트 - Ticketing DB
-- VU별 사용자 6명: user_iteration 1~4는 결제 2건, 5~6은 결제 1건
-- VU별 결제 10건, 전체 사용자 600명, 전체 결제 1,000건

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
    vu_id,
    user_iteration,
    9100000 + ((vu_id - 1) * 6) + user_iteration AS user_id,
    ('81000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS reservation_id,
    ('82000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS payment_id,
    ('83000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS schedule_seat_id,
    ('84000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS seat_hold_id,
    ('85000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS reservation_seat_id,
    ('86000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS venue_seat_id,
    ('89000000-0000-0000-0000-' || lpad(global_index::text, 12, '0'))::uuid AS hold_token
FROM mapped;

INSERT INTO p_schedule_seat (
    schedule_seat_id, event_session_id, venue_seat_id,
    section, row_label, seat_number, seat_grade, price, seat_status,
    created_at, created_by, updated_at, updated_by
)
SELECT
    schedule_seat_id,
    '87000000-0000-0000-0000-000000000001'::uuid,
    venue_seat_id,
    'S08F',
    'S08F-' || lpad((((global_index - 1) / 100) + 1)::text, 2, '0'),
    (((global_index - 1) % 100) + 1)::text,
    'VIP',
    150000,
    'HELD',
    CURRENT_TIMESTAMP,
    user_id,
    CURRENT_TIMESTAMP,
    user_id
FROM s08_failure_fixture;

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
    'S08F-SEAT-HOLD-IDEMP-' || lpad(global_index::text, 12, '0'),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM s08_failure_fixture;

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
    'S08F-RES-' || lpad(global_index::text, 12, '0'),
    '[S08] Kafka 실패 격리 테스트 공연',
    CURRENT_TIMESTAMP + INTERVAL '1 day',
    1,
    150000,
    'PAYMENT_PROCESSING',
    NULL,
    NULL,
    'S08F-RES-IDEMP-' || lpad(global_index::text, 12, '0'),
    CURRENT_TIMESTAMP,
    user_id,
    CURRENT_TIMESTAMP,
    user_id,
    NULL,
    NULL,
    FALSE,
    0
FROM s08_failure_fixture;

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
FROM s08_failure_fixture;

-- Inbox와 Outbox는 Consumer 처리 전 0건이어야 합니다.
COMMIT;
