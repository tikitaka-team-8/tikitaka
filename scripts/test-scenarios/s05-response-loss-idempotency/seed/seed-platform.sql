-- S05 응답 유실 멱등성 테스트 - Platform Fixture
-- 예매 생성 시 Ticketing Service가 조회할 공연·회차 스냅샷을 준비합니다.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO p_user (
    id, email, password_hash, name, nickname, phone, role, status
) VALUES (
    9500000,
    's05-organizer@test.tikitaka.local',
    'fixture-user-not-for-login',
    '[S05] Response Loss Organizer',
    's05-response-loss-organizer',
    NULL,
    'ORGANIZER',
    'ACTIVE'
)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    nickname = EXCLUDED.nickname,
    phone = EXCLUDED.phone,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO p_organizer (
    id, user_id, name, representative_name, contact_email,
    contact_phone, description, status, approved_at
) VALUES (
    '50050000-0000-0000-0000-000000000001',
    9500000,
    '[S05] Response Loss Agency',
    'S05 Test Manager',
    's05-organizer@test.tikitaka.local',
    '010-0500-0001',
    'S05 response loss idempotency test organizer',
    'ACTIVE',
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    name = EXCLUDED.name,
    representative_name = EXCLUDED.representative_name,
    contact_email = EXCLUDED.contact_email,
    contact_phone = EXCLUDED.contact_phone,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    approved_at = EXCLUDED.approved_at;

INSERT INTO p_venue (
    id, name, postal_code, address, address_detail, contact_phone, active
) VALUES (
    '50050000-0000-0000-0000-000000000002',
    '[S05] Response Loss Test Hall',
    '05000',
    'S05 Test Address 5',
    'S05 Test Venue',
    '02-0500-0002',
    TRUE
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    postal_code = EXCLUDED.postal_code,
    address = EXCLUDED.address,
    address_detail = EXCLUDED.address_detail,
    contact_phone = EXCLUDED.contact_phone,
    active = EXCLUDED.active;

INSERT INTO p_venue_section (
    id, venue_id, name, floor_label, display_order, active
) VALUES (
    '50050000-0000-0000-0000-000000000003',
    '50050000-0000-0000-0000-000000000002',
    'S05',
    '1F',
    1,
    TRUE
)
ON CONFLICT (id) DO UPDATE SET
    venue_id = EXCLUDED.venue_id,
    name = EXCLUDED.name,
    floor_label = EXCLUDED.floor_label,
    display_order = EXCLUDED.display_order,
    active = EXCLUDED.active;

INSERT INTO p_venue_seat (
    id, section_id, row_label, seat_number, active
) VALUES (
    '50050000-0000-0000-0000-000000000004',
    '50050000-0000-0000-0000-000000000003',
    'S05',
    '1',
    TRUE
)
ON CONFLICT (id) DO UPDATE SET
    section_id = EXCLUDED.section_id,
    row_label = EXCLUDED.row_label,
    seat_number = EXCLUDED.seat_number,
    active = EXCLUDED.active;

INSERT INTO p_event (
    id, organizer_id, venue_id, title, description, running_time_minutes, status
) VALUES (
    '50050000-0000-0000-0000-000000000005',
    '50050000-0000-0000-0000-000000000001',
    '50050000-0000-0000-0000-000000000002',
    '[S05] Response Loss Idempotency Event',
    'S05 reservation response loss test event',
    120,
    'ON_SALE'
)
ON CONFLICT (id) DO UPDATE SET
    organizer_id = EXCLUDED.organizer_id,
    venue_id = EXCLUDED.venue_id,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    running_time_minutes = EXCLUDED.running_time_minutes,
    status = EXCLUDED.status;

INSERT INTO p_event_session (
    id, event_id, session_number,
    performance_start_at, performance_end_at,
    sales_open_at, sales_close_at,
    status, queue_enabled
) VALUES (
    '50050000-0000-0000-0000-000000000006',
    '50050000-0000-0000-0000-000000000005',
    1,
    CURRENT_TIMESTAMP + INTERVAL '3 hours',
    CURRENT_TIMESTAMP + INTERVAL '5 hours',
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP + INTERVAL '2 hours',
    'SCHEDULED',
    FALSE
)
ON CONFLICT (id) DO UPDATE SET
    event_id = EXCLUDED.event_id,
    session_number = EXCLUDED.session_number,
    performance_start_at = EXCLUDED.performance_start_at,
    performance_end_at = EXCLUDED.performance_end_at,
    sales_open_at = EXCLUDED.sales_open_at,
    sales_close_at = EXCLUDED.sales_close_at,
    status = EXCLUDED.status,
    queue_enabled = EXCLUDED.queue_enabled;

INSERT INTO p_session_section_price (
    id, event_session_id, venue_section_id, seat_grade, price_amount, sales_enabled
) VALUES (
    '50050000-0000-0000-0000-000000000007',
    '50050000-0000-0000-0000-000000000006',
    '50050000-0000-0000-0000-000000000003',
    'S05',
    150000,
    TRUE
)
ON CONFLICT (id) DO UPDATE SET
    event_session_id = EXCLUDED.event_session_id,
    venue_section_id = EXCLUDED.venue_section_id,
    seat_grade = EXCLUDED.seat_grade,
    price_amount = EXCLUDED.price_amount,
    sales_enabled = EXCLUDED.sales_enabled;

COMMIT;
