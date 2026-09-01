package com.tikitaka.paymentnotification.payment.application.result;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentCreateResult(
        UUID paymentId,
        UUID reservationId,
        String orderId,
        Long amount,
        String currency,
        PaymentProvider paymentProvider,
        PaymentStatus status,
        OffsetDateTime requestedAt
) {

    public static PaymentCreateResult from(Payment payment) {
        return new PaymentCreateResult(
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentProvider(),
                payment.getStatus(),
                payment.getRequestedAt()
        );
    }
}