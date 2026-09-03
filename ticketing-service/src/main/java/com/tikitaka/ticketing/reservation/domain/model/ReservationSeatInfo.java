package com.tikitaka.ticketing.reservation.domain.model;

import java.util.UUID;

public record ReservationSeatInfo(
        UUID scheduleSeatId,
        String section,
        String rowLabel,
        String seatNumber,
        String seatGrade,
        Long price
) {
}
