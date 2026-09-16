-- S08 Kafka 장애·복구 테스트 - 공통 Ticketing Fixture cleanup
-- Inbox·Outbox와 도메인 데이터를 FK 의존 순서에 맞춰 S08 전용 ID만 정리합니다.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE s08_cleanup_reservation_ids ON COMMIT DROP AS
SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
FROM generate_series(1, 1000) AS gs;

DELETE FROM p_reservation_inbox
WHERE reservation_id IN (SELECT reservation_id FROM s08_cleanup_reservation_ids);

DELETE FROM p_reservation_outbox
WHERE reservation_id IN (SELECT reservation_id FROM s08_cleanup_reservation_ids);

DELETE FROM p_reservation_seats
WHERE reservation_id IN (SELECT reservation_id FROM s08_cleanup_reservation_ids);

DELETE FROM p_reservation
WHERE reservation_id IN (SELECT reservation_id FROM s08_cleanup_reservation_ids)
  AND (reservation_number LIKE 'S08-RES-%' OR reservation_number LIKE 'S08F-RES-%');

DELETE FROM p_seat_hold
WHERE seat_hold_id IN (
    SELECT ('84000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid
    FROM generate_series(1, 1000) AS gs
)
  AND (idempotency_key LIKE 'S08-SEAT-HOLD-IDEMP-%'
       OR idempotency_key LIKE 'S08F-SEAT-HOLD-IDEMP-%');

DELETE FROM p_schedule_seat
WHERE schedule_seat_id IN (
    SELECT ('83000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid
    FROM generate_series(1, 1000) AS gs
)
  AND section IN ('S08', 'S08F');

COMMIT;
