-- S08 Retry 성공 보충 테스트 - Notification 결과 검증

\set ON_ERROR_STOP on
\pset pager off

SELECT
    COUNT(*) AS notification_count,
    COUNT(*) FILTER (WHERE notification_type = 'RESERVATION_CONFIRMED') AS confirmed_count,
    COUNT(DISTINCT source_event_id) AS unique_source_event_count
FROM p_notification
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

SELECT
    COUNT(*) AS notification_inbox_count,
    COUNT(*) FILTER (WHERE event_type = 'RESERVATION_CONFIRMED') AS confirmed_inbox_count,
    COUNT(DISTINCT event_id) AS unique_event_count
FROM p_notification_inbox
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

SELECT last_value AS notification_attempt_count
FROM s08_retry_success_notification_attempt_seq;
