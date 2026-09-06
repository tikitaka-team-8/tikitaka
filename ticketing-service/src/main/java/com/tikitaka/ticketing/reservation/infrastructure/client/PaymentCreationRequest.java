package com.tikitaka.ticketing.reservation.infrastructure.client;

import java.util.UUID;

public record PaymentCreationRequest(
        UUID reservationId,
        Long userId,
        Long totalAmount
) {
}
