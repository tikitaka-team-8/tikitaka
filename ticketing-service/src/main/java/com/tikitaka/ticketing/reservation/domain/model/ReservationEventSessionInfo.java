package com.tikitaka.ticketing.reservation.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationEventSessionInfo(
        UUID eventSessionId,
        UUID eventId,
        String eventTitle,
        OffsetDateTime sessionStartAt
) {
}
