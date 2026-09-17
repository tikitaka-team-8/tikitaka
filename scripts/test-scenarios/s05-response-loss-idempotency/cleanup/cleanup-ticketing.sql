-- S05 응답 유실 멱등성 테스트 - Ticketing cleanup
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE s05_cleanup_reservations ON COMMIT DROP AS
SELECT reservation_id
FROM p_reservation
WHERE idempotency_key LIKE 's05-%';

DELETE FROM p_reservation_inbox
WHERE reservation_id IN (SELECT reservation_id FROM s05_cleanup_reservations);

DELETE FROM p_reservation_outbox
WHERE reservation_id IN (SELECT reservation_id FROM s05_cleanup_reservations);

DELETE FROM p_reservation_seats
WHERE reservation_id IN (SELECT reservation_id FROM s05_cleanup_reservations);

DELETE FROM p_reservation
WHERE reservation_id IN (SELECT reservation_id FROM s05_cleanup_reservations)
  AND idempotency_key LIKE 's05-%';

DELETE FROM p_seat_hold
WHERE seat_hold_id = '52050000-0000-0000-0000-000000000001'
  AND idempotency_key = 's05-seat-hold-response-loss-01';

DELETE FROM p_schedule_seat
WHERE schedule_seat_id = '51050000-0000-0000-0000-000000000001'
  AND section = 'S05';

COMMIT;
