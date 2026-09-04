package com.tikitaka.ticketing.seat.presentation.dto.response;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SeatHoldResponse(
        UUID seatHoldId,
        UUID holdToken,
        Instant expiresAt
) {

    public static SeatHoldResponse from(SeatHold seatHold) {
        return new SeatHoldResponse(
                seatHold.getSeatHoldId(),
                seatHold.getHoldToken(),
                seatHold.getExpiresAt()
        );
    }
}
