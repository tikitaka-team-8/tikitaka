-- S08 Kafka 실패 격리 테스트 - Ticketing DB cleanup
-- 고정 S08 UUID 범위를 사용하므로 기존 정상 복구 Fixture도 함께 정리할 수 있습니다.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE s08_failure_reservation_ids ON COMMIT DROP AS
SELECT ('81000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid AS reservation_id
FROM generate_series(1, 1000) AS gs;

DELETE FROM p_reservation_inbox
WHERE reservation_id IN (SELECT reservation_id FROM s08_failure_reservation_ids);

DELETE FROM p_reservation_outbox
WHERE reservation_id IN (SELECT reservation_id FROM s08_failure_reservation_ids);

DELETE FROM p_reservation_seats
WHERE reservation_id IN (SELECT reservation_id FROM s08_failure_reservation_ids);

DELETE FROM p_reservation
WHERE reservation_id IN (SELECT reservation_id FROM s08_failure_reservation_ids);

DELETE FROM p_seat_hold
WHERE seat_hold_id IN (
    SELECT ('84000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid
    FROM generate_series(1, 1000) AS gs
);

DELETE FROM p_schedule_seat
WHERE schedule_seat_id IN (
    SELECT ('83000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid
    FROM generate_series(1, 1000) AS gs
);

COMMIT;
