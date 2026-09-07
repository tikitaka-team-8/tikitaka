package com.tikitaka.paymentnotification.notification.application.command;

import java.time.Instant;
import java.util.UUID;

public record ReservationFailedNotificationCommand(
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
