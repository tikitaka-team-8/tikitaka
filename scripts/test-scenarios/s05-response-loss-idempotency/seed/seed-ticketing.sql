-- S05 응답 유실 멱등성 테스트 - Ticketing Fixture
-- Gateway 인증 흐름을 제외하기 위해 고정 테스트 사용자 ID 9500001을 사용합니다.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO p_schedule_seat (
    schedule_seat_id, event_session_id, venue_seat_id,
    section, row_label, seat_number, seat_grade, price, seat_status,
    created_by, updated_by
) VALUES (
    '51050000-0000-0000-0000-000000000001',
    '50050000-0000-0000-0000-000000000006',
    '50050000-0000-0000-0000-000000000004',
    'S05',
    'S05',
    '1',
    'S05',
    150000,
    'HELD',
    9500001,
    9500001
)
ON CONFLICT (schedule_seat_id) DO UPDATE SET
    event_session_id = EXCLUDED.event_session_id,
    venue_seat_id = EXCLUDED.venue_seat_id,
    section = EXCLUDED.section,
    row_label = EXCLUDED.row_label,
    seat_number = EXCLUDED.seat_number,
    seat_grade = EXCLUDED.seat_grade,
    price = EXCLUDED.price,
    seat_status = EXCLUDED.seat_status,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = EXCLUDED.updated_by,
    deleted_at = NULL,
    deleted_by = NULL;

INSERT INTO p_seat_hold (
    seat_hold_id, schedule_seat_id, user_id, hold_token,
    hold_status, held_at, expires_at, released_at, release_reason,
    idempotency_key, extended_at, reserved_at,
    created_by, updated_by, deleted_at, deleted_by
) VALUES (
    '52050000-0000-0000-0000-000000000001',
    '51050000-0000-0000-0000-000000000001',
    9500001,
    '53050000-0000-0000-0000-000000000001',
    'HOLDING',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '30 minutes',
    NULL,
    NULL,
    's05-seat-hold-response-loss-01',
    NULL,
    NULL,
    9500001,
    9500001,
    NULL,
    NULL
)
ON CONFLICT (seat_hold_id) DO UPDATE SET
    schedule_seat_id = EXCLUDED.schedule_seat_id,
    user_id = EXCLUDED.user_id,
    hold_token = EXCLUDED.hold_token,
    hold_status = EXCLUDED.hold_status,
    held_at = EXCLUDED.held_at,
    expires_at = EXCLUDED.expires_at,
    released_at = NULL,
    release_reason = NULL,
    idempotency_key = EXCLUDED.idempotency_key,
    extended_at = NULL,
    reserved_at = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = EXCLUDED.updated_by,
    deleted_at = NULL,
    deleted_by = NULL;

COMMIT;
