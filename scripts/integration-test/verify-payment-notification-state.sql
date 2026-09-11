\set ON_ERROR_STOP on

SELECT concat_ws(
    '|',
    COALESCE(payment.status, 'ROW_NOT_FOUND'),
    COUNT(DISTINCT payment_outbox.outbox_id),
    COUNT(DISTINCT payment_outbox.outbox_id) FILTER (WHERE payment_outbox.status = 'PUBLISHED'),
    COUNT(DISTINCT notification.notification_id),
    COALESCE(MAX(notification.read_status), 'ROW_NOT_FOUND'),
    CASE
        WHEN COUNT(DISTINCT notification.notification_id) = 1
            AND BOOL_AND(notification.last_viewed_at IS NOT NULL)
        THEN 'VIEWED'
        ELSE 'NOT_VIEWED'
    END,
    COUNT(DISTINCT notification_inbox.event_id)
)
FROM p_payment AS payment
LEFT JOIN p_payment_outbox AS payment_outbox
    ON payment_outbox.payment_id = payment.payment_id
    AND payment_outbox.event_type = 'PAYMENT_SUCCEEDED'
LEFT JOIN p_notification AS notification
    ON notification.notification_id = :'notification_id'::uuid
    AND notification.reservation_id = payment.reservation_id
    AND notification.reservation_number = :'reservation_number'
    AND notification.user_id = payment.user_id
    AND notification.notification_type = 'RESERVATION_CONFIRMED'
    AND notification.is_deleted = FALSE
LEFT JOIN p_notification_inbox AS notification_inbox
    ON notification_inbox.event_id = notification.source_event_id
    AND notification_inbox.reservation_id = notification.reservation_id
    AND notification_inbox.event_type = notification.notification_type
WHERE payment.payment_id = :'payment_id'::uuid
  AND payment.reservation_id = :'reservation_id'::uuid
GROUP BY payment.status;
