-- S08 Retry 성공 보충 테스트 - Ticketing 결과 검증

\set ON_ERROR_STOP on
\pset pager off

SELECT
    r.reservation_id,
    r.reservation_status,
    r.payment_completed_at IS NOT NULL AS payment_completed,
    r.version,
    sh.hold_status,
    ss.seat_status
FROM p_reservation r
JOIN p_reservation_seats rs ON rs.reservation_id = r.reservation_id
JOIN p_seat_hold sh ON sh.seat_hold_id = rs.seat_hold_id
JOIN p_schedule_seat ss ON ss.schedule_seat_id = rs.schedule_seat_id
WHERE r.reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

SELECT
    COUNT(*) FILTER (
        WHERE event_id = 'f3000000-0000-0000-0000-000000000001'::uuid
    ) AS retry_success_inbox_count,
    COUNT(DISTINCT event_id) FILTER (
        WHERE event_id = 'f3000000-0000-0000-0000-000000000001'::uuid
    ) AS retry_success_unique_event_count
FROM p_reservation_inbox;

SELECT
    COUNT(*) AS reservation_outbox_count,
    COUNT(*) FILTER (WHERE status = 'PUBLISHED') AS published_count,
    COUNT(DISTINCT event_id) AS unique_event_count
FROM p_reservation_outbox
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

SELECT last_value AS ticketing_attempt_count
FROM s08_retry_success_ticketing_attempt_seq;
