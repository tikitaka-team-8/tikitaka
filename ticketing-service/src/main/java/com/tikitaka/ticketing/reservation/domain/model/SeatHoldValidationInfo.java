package com.tikitaka.ticketing.reservation.domain.model;

import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;

import java.time.Instant;
import java.util.UUID;

public record SeatHoldValidationInfo(
        UUID seatHoldId,
        Long userId,
        HoldStatus holdStatus,
        Instant expiresAt
) {
}
