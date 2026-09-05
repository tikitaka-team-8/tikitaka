package com.tikitaka.paymentnotification.payment.infrastructure.persistence.reservation;

import java.util.UUID;

public record ReservationPaymentValidationResponse (
        UUID reservationId,
        Long userId,
        Long totalAmount
){

}
