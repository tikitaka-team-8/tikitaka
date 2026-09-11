-- 예매 생성 시점에 SeatHold가 결제 처리 권한을 원자적으로 획득했음을 나타내는 RESERVED 상태 추가.
-- RESERVED인 동안은 만료 스케줄러 대상(findByHoldStatusAndExpiresAtBeforeOrderByExpiresAtAsc)에서
-- 제외되어, 결제 처리 중인 좌석 선점이 만료 처리와 경쟁하지 않는다.

ALTER TABLE p_seat_hold
    ADD COLUMN reserved_at TIMESTAMPTZ;

ALTER TABLE p_seat_hold
    DROP CONSTRAINT ck_seat_hold_status;

ALTER TABLE p_seat_hold
    ADD CONSTRAINT ck_seat_hold_status
        CHECK (hold_status IN ('HOLDING', 'RESERVED', 'CONFIRMED', 'EXPIRED', 'RELEASED'));
