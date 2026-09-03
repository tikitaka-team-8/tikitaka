package com.tikitaka.ticketing.reservation.domain.model;

import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationCreationSeatInfo(
        UUID seatHoldId,
        UUID scheduleSeatId,
        Long userId,
        HoldStatus holdStatus,
        OffsetDateTime expiresAt,
        UUID eventSessionId,
        Long price
) {
}
