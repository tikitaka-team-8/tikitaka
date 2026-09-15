-- S08 Kafka 장애·복구 부하 테스트 - Ticketing DB cleanup
-- 테스트 실행 후 생성된 Inbox/Outbox부터 FK 의존 순서에 맞춰 S08 전용 ID만 정리합니다.
\set ON_ERROR_STOP on
BEGIN;

WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
)
DELETE FROM p_reservation_inbox
WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids);

WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
)
DELETE FROM p_reservation_outbox
WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids);

WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
)
DELETE FROM p_reservation_seats
WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids);

WITH s08_reservation_ids AS (
    SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
    FROM generate_series(1, 1000) AS gs
)
DELETE FROM p_reservation
WHERE reservation_id IN (SELECT reservation_id FROM s08_reservation_ids)
  AND reservation_number LIKE 'S08-RES-%';

WITH s08_seat_hold_ids AS (
    SELECT ('84000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS seat_hold_id
    FROM generate_series(1, 1000) AS gs
)
DELETE FROM p_seat_hold
WHERE seat_hold_id IN (SELECT seat_hold_id FROM s08_seat_hold_ids)
  AND idempotency_key LIKE 'S08-SEAT-HOLD-IDEMP-%';

WITH s08_schedule_seat_ids AS (
    SELECT ('83000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS schedule_seat_id
    FROM generate_series(1, 1000) AS gs
)
DELETE FROM p_schedule_seat
WHERE schedule_seat_id IN (SELECT schedule_seat_id FROM s08_schedule_seat_ids);

COMMIT;
