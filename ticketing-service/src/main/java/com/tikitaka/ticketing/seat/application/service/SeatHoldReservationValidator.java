package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;

import java.util.UUID;

public interface SeatHoldReservationValidator {

// (HOLDING -> RESERVED) 예매 생성 시 결제 처리 권한을 원자적으로 획득한다.
    void validateAndExtend(UUID seatHoldId);

//  (RESERVED -> CONFIRMED, HELD -> SOLD)
    void confirmHold(UUID seatHoldId);

//  (HOLDING/RESERVED -> RELEASED, HELD -> AVAILABLE)
    void releaseHold(UUID seatHoldId, ReleaseReason reason);
}
