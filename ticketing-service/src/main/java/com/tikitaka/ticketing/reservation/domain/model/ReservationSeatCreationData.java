package com.tikitaka.ticketing.reservation.domain.model;

import java.util.UUID;

public record ReservationSeatCreationData(
        UUID seatHoldId,
        UUID scheduleSeatId,
        Long price
) {
}
