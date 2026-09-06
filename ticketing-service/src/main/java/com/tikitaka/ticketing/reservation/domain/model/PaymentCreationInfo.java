package com.tikitaka.ticketing.reservation.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentCreationInfo(
        UUID paymentId,
        UUID reservationId,
        String orderId,
        Long amount,
        String status,
        OffsetDateTime createdAt
) {
}
