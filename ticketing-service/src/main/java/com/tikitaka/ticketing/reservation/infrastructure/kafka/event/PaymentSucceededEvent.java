package com.tikitaka.ticketing.reservation.infrastructure.kafka.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID aggregateId,
        Integer version,
        UUID paymentId,
        UUID reservationId,
        Long userId,
        Long amount,
        OffsetDateTime approvedAt
) {
}
