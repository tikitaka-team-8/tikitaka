package com.tikitaka.paymentnotification.notification.infrastructure.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationFailedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID reservationId,
        String reservationNumber,
        Long userId,
        String eventTitle,
        Instant sessionStartAt,
        String failureReason
) {
}
