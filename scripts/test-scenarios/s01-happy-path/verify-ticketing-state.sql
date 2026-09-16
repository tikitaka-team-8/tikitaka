\set ON_ERROR_STOP on

SELECT concat_ws(
    '|',
    reservation.reservation_status,
    seat_hold.hold_status,
    schedule_seat.seat_status
)
FROM p_reservation AS reservation
JOIN p_reservation_seats AS reservation_seat
    ON reservation_seat.reservation_id = reservation.reservation_id
    AND reservation_seat.is_deleted = FALSE
JOIN p_seat_hold AS seat_hold
    ON seat_hold.seat_hold_id = reservation_seat.seat_hold_id
JOIN p_schedule_seat AS schedule_seat
    ON schedule_seat.schedule_seat_id = reservation_seat.schedule_seat_id
WHERE reservation.reservation_id = :'reservation_id'::uuid
  AND reservation_seat.seat_hold_id = :'seat_hold_id'::uuid
  AND reservation_seat.schedule_seat_id = :'schedule_seat_id'::uuid
  AND reservation.is_deleted = FALSE;
