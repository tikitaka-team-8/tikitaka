-- S08 Kafka 장애·복구 테스트 - Ticketing DB 최종 상태 검증
-- 초기 Consumer 중단 구간과 복구 완료 구간에서 반복 실행하여 상태 변화를 비교합니다.
\set ON_ERROR_STOP on
\pset pager off

-- 예매 상태: 중단 중에는 PAYMENT_PROCESSING, 복구 후에는 CONFIRMED 1,000건이 기대값입니다.
WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
)
SELECT
    COUNT(*) AS reservation_total,
    COUNT(*) FILTER (WHERE reservation_status = 'PAYMENT_PROCESSING') AS payment_processing_count,
    COUNT(*) FILTER (WHERE reservation_status = 'CONFIRMED') AS confirmed_count,
    COUNT(*) FILTER (WHERE reservation_status = 'FAILED') AS failed_count
FROM p_reservation
WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids);

-- 결제 이벤트 소비 및 예매 결과 이벤트 발행 상태를 확인합니다.
WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
),
inbox_summary AS (
    SELECT
        COUNT(*) AS inbox_total,
        COUNT(DISTINCT event_id) AS inbox_unique_events,
        COUNT(DISTINCT reservation_id) AS inbox_unique_reservations
    FROM p_reservation_inbox
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
),
outbox_summary AS (
    SELECT
        COUNT(*) AS outbox_total,
        COUNT(*) FILTER (WHERE status = 'PENDING') AS outbox_pending_count,
        COUNT(*) FILTER (WHERE status = 'PUBLISHED') AS outbox_published_count,
        COUNT(DISTINCT reservation_id) AS outbox_unique_reservations
    FROM p_reservation_outbox
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
)
SELECT *
FROM inbox_summary
CROSS JOIN outbox_summary;

-- 좌석 상태: 복구 후 SeatHold CONFIRMED, ScheduleSeat SOLD 1,000건이 기대값입니다.
WITH s08_seat_ids AS (
    SELECT
        ('83000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS schedule_seat_id,
        ('84000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS seat_hold_id
    FROM generate_series(1, 1000) AS gs
),
seat_hold_summary AS (
    SELECT
        COUNT(*) AS seat_hold_total,
        COUNT(*) FILTER (WHERE hold_status = 'RESERVED') AS seat_hold_reserved_count,
        COUNT(*) FILTER (WHERE hold_status = 'CONFIRMED') AS seat_hold_confirmed_count
    FROM p_seat_hold
    WHERE seat_hold_id IN (SELECT seat_hold_id FROM s08_seat_ids)
),
schedule_seat_summary AS (
    SELECT
        COUNT(*) AS schedule_seat_total,
        COUNT(*) FILTER (WHERE seat_status = 'HELD') AS schedule_seat_held_count,
        COUNT(*) FILTER (WHERE seat_status = 'SOLD') AS schedule_seat_sold_count
    FROM p_schedule_seat
    WHERE schedule_seat_id IN (SELECT schedule_seat_id FROM s08_seat_ids)
)
SELECT *
FROM seat_hold_summary
CROSS JOIN schedule_seat_summary;

-- 한 Reservation에 Inbox 또는 Outbox가 중복 생성된 경우에만 행이 출력됩니다.
WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
),
duplicates AS (
    SELECT 'INBOX' AS source, reservation_id, COUNT(*) AS duplicate_count
    FROM p_reservation_inbox
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
    GROUP BY reservation_id
    HAVING COUNT(*) > 1

    UNION ALL

    SELECT 'OUTBOX' AS source, reservation_id, COUNT(*) AS duplicate_count
    FROM p_reservation_outbox
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
    GROUP BY reservation_id
    HAVING COUNT(*) > 1
)
SELECT *
FROM duplicates
ORDER BY source, reservation_id;
