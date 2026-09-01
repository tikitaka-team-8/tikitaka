package com.tikitaka.paymentnotification.payment.presentation.dto;

import com.tikitaka.paymentnotification.payment.application.result.PaymentCreateResult;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentCreateResponse(
        UUID paymentId,
        UUID reservationId,
        String orderId,
        Long amount,
        String currency,
        PaymentProvider paymentProvider,
        PaymentStatus status,
        OffsetDateTime requestedAt
) {

    public static PaymentCreateResponse from(PaymentCreateResult result) {
        return new PaymentCreateResponse(
                result.paymentId(),
                result.reservationId(),
                result.orderId(),
                result.amount(),
                result.currency(),
                result.paymentProvider(),
                result.status(),
                result.requestedAt()
        );
    }
}