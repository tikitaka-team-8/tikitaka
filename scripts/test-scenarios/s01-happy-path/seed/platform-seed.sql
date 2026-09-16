-- Platform PostgreSQL
-- Host port: 5433
-- 로컬 테스트용 seed

BEGIN;

INSERT INTO p_user (
    id, email, password_hash, name, nickname, phone, role, status
) VALUES (
    900001,
    'fixture-organizer@tikitaka.local',
    'fixture-user-not-for-login',
    '통합테스트 주최자',
    'fixture-organizer',
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
    '10000000-0000-0000-0000-000000000001',
    900001,
    '티키타카 테스트 기획사',
    '테스트 담당자',
    'fixture-organizer@tikitaka.local',
    '010-0000-0000',
    '1차 로컬 통합 테스트 전용 주최자',
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
    '20000000-0000-0000-0000-000000000001',
    '티키타카 테스트홀',
    '00000',
    '서울특별시 테스트구 통합로 1',
    '테스트 공연장',
    '02-0000-0000',
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
) VALUES
    (
        '21000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'VIP',
        '1F',
        1,
        TRUE
    ),
    (
        '21000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        'R',
        '1F',
        2,
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
) VALUES
    ('22000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', 'A', '1', TRUE),
    ('22000000-0000-0000-0000-000000000002', '21000000-0000-0000-0000-000000000001', 'A', '2', TRUE),
    ('22000000-0000-0000-0000-000000000003', '21000000-0000-0000-0000-000000000001', 'A', '3', TRUE),
    ('22000000-0000-0000-0000-000000000004', '21000000-0000-0000-0000-000000000001', 'A', '4', TRUE),
    ('22000000-0000-0000-0000-000000000005', '21000000-0000-0000-0000-000000000002', 'B', '1', TRUE),
    ('22000000-0000-0000-0000-000000000006', '21000000-0000-0000-0000-000000000002', 'B', '2', TRUE),
    ('22000000-0000-0000-0000-000000000007', '21000000-0000-0000-0000-000000000002', 'B', '3', TRUE),
    ('22000000-0000-0000-0000-000000000008', '21000000-0000-0000-0000-000000000002', 'B', '4', TRUE)
ON CONFLICT (id) DO UPDATE SET
    section_id = EXCLUDED.section_id,
    row_label = EXCLUDED.row_label,
    seat_number = EXCLUDED.seat_number,
    active = EXCLUDED.active;

INSERT INTO p_event (
    id, organizer_id, venue_id, title, description, running_time_minutes, status
) VALUES (
    '30000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '[통합테스트] 티키타카 콘서트',
    '1차 로컬 통합 테스트를 위한 공개 공연입니다.',
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
    '31000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    1,
    CURRENT_TIMESTAMP + INTERVAL '3 hours',
    CURRENT_TIMESTAMP + INTERVAL '5 hours',
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP + INTERVAL '2 hours',
    'SCHEDULED',
    TRUE
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
) VALUES
    (
        '32000000-0000-0000-0000-000000000001',
        '31000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        'VIP',
        150000,
        TRUE
    ),
    (
        '32000000-0000-0000-0000-000000000002',
        '31000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000002',
        'R',
        100000,
        TRUE
    )
ON CONFLICT (id) DO UPDATE SET
    event_session_id = EXCLUDED.event_session_id,
    venue_section_id = EXCLUDED.venue_section_id,
    seat_grade = EXCLUDED.seat_grade,
    price_amount = EXCLUDED.price_amount,
    sales_enabled = EXCLUDED.sales_enabled;

COMMIT;


