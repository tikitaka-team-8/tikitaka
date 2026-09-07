package com.tikitaka.paymentnotification.payment.application.result;

import java.util.UUID;

public record ReservationPaymentValidationResult(
        UUID reservationId,
        Long userId,
        Long totalAmount
) {
}
