-- S08 Kafka 장애·복구 테스트 - Payment & Notification DB 최종 상태 검증
-- 결제 승인 직후와 Notification Consumer 복구 후 반복 실행하여 상태 변화를 비교합니다.
\set ON_ERROR_STOP on
\pset pager off

-- k6 완료 후 Payment APPROVED 및 Payment Outbox PUBLISHED 1,000건이 기대값입니다.
WITH s08_payment_ids AS (
    SELECT ('82000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS payment_id
    FROM generate_series(1, 1000) AS gs
),
payment_summary AS (
    SELECT
        COUNT(*) AS payment_total,
        COUNT(*) FILTER (WHERE status = 'READY') AS payment_ready_count,
        COUNT(*) FILTER (WHERE status = 'PROCESSING') AS payment_processing_count,
        COUNT(*) FILTER (WHERE status = 'APPROVED') AS payment_approved_count,
        COUNT(*) FILTER (WHERE status = 'FAILED') AS payment_failed_count
    FROM p_payment
    WHERE payment_id IN (SELECT payment_id FROM s08_payment_ids)
),
payment_outbox_summary AS (
    SELECT
        COUNT(*) AS payment_outbox_total,
        COUNT(*) FILTER (WHERE status = 'PENDING') AS payment_outbox_pending_count,
        COUNT(*) FILTER (WHERE status = 'PUBLISHED') AS payment_outbox_published_count,
        COUNT(*) FILTER (WHERE status = 'FAILED') AS payment_outbox_failed_count,
        COUNT(DISTINCT payment_id) AS payment_outbox_unique_payments
    FROM p_payment_outbox
    WHERE payment_id IN (SELECT payment_id FROM s08_payment_ids)
)
SELECT *
FROM payment_summary
CROSS JOIN payment_outbox_summary;

-- Notification Consumer 중단 중에는 0건, 복구 후에는 알림과 Inbox 각각 1,000건이 기대값입니다.
WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
),
notification_summary AS (
    SELECT
        COUNT(*) AS notification_total,
        COUNT(*) FILTER (WHERE notification_type = 'RESERVATION_CONFIRMED') AS confirmed_notification_count,
        COUNT(*) FILTER (WHERE read_status = 'UNREAD' AND last_viewed_at IS NULL) AS unread_count,
        COUNT(DISTINCT source_event_id) AS notification_unique_events,
        COUNT(DISTINCT reservation_id) AS notification_unique_reservations
    FROM p_notification
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
),
inbox_summary AS (
    SELECT
        COUNT(*) AS notification_inbox_total,
        COUNT(DISTINCT event_id) AS notification_inbox_unique_events,
        COUNT(DISTINCT reservation_id) AS notification_inbox_unique_reservations
    FROM p_notification_inbox
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
)
SELECT *
FROM notification_summary
CROSS JOIN inbox_summary;

-- 한 Reservation에 알림 또는 Inbox가 중복 생성된 경우에만 행이 출력됩니다.
WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
),
duplicates AS (
    SELECT 'NOTIFICATION' AS source, reservation_id, COUNT(*) AS duplicate_count
    FROM p_notification
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
    GROUP BY reservation_id
    HAVING COUNT(*) > 1

    UNION ALL

    SELECT 'INBOX' AS source, reservation_id, COUNT(*) AS duplicate_count
    FROM p_notification_inbox
    WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
    GROUP BY reservation_id
    HAVING COUNT(*) > 1
)
SELECT *
FROM duplicates
ORDER BY source, reservation_id;
