package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;

import java.util.UUID;

public interface SeatHoldReservationValidator {

    void validateAndExtend(UUID seatHoldId);

//  (HOLDING -> CONFIRMED, HELD -> SOLD)
    void confirmHold(UUID seatHoldId);

//  (HOLDING -> RELEASED, HELD -> AVAILABLE)
    void releaseHold(UUID seatHoldId, ReleaseReason reason);
}
