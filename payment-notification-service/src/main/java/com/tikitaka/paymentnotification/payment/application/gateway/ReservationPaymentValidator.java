package com.tikitaka.paymentnotification.payment.application.gateway;

import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;

import java.util.UUID;

public interface ReservationPaymentValidator {

    ReservationPaymentValidationResult validate(
            UUID reservationId,
            Long userId
    );
}
