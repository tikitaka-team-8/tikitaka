\set ON_ERROR_STOP on

SELECT concat_ws(
    '|',
    COUNT(*),
    COUNT(*) FILTER (WHERE status = 'PUBLISHED')
)
FROM p_reservation_outbox
WHERE reservation_id = :'reservation_id'::uuid
  AND event_type = 'RESERVATION_CONFIRMED';
