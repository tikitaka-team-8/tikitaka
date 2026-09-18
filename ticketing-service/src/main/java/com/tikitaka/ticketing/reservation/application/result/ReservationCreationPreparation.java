package com.tikitaka.ticketing.reservation.application.result;

import java.util.UUID;

public record ReservationCreationPreparation(
        UUID reservationId,
        Long userId,
        Long totalAmount,
        String idempotencyKey,
        boolean created,
        boolean paymentCreationRequired,
        CreateReservationResult reservationResult
) {
}
