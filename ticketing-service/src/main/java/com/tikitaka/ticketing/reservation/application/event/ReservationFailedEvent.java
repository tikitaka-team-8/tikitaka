package com.tikitaka.ticketing.reservation.application.event;

import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxEventType;

import java.time.Instant;
import java.util.UUID;

public record ReservationFailedEvent(
        UUID eventId,
        ReservationOutboxEventType eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID reservationId,
        String reservationNumber,
        Long userId,
        String eventTitle,
        Instant sessionStartAt,
        ReservationFailureReason failureReason
) {
}
