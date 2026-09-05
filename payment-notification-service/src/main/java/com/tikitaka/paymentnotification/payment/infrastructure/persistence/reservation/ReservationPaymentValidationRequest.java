package com.tikitaka.paymentnotification.payment.infrastructure.persistence.reservation;

public record ReservationPaymentValidationRequest(
        // Payment -> Ticketing Body 값
        Long userId
) {
}