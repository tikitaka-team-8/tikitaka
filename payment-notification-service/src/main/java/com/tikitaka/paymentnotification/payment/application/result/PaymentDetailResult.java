package com.tikitaka.paymentnotification.payment.application.result;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentDetailResult(
        UUID paymentId,
        UUID reservationId,
        String orderId,
        Long amount,
        PaymentStatus status,
        String currency,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        OffsetDateTime approvedAt,
        OffsetDateTime canceledAt
) {

    public static PaymentDetailResult from(Payment payment) {
        return new PaymentDetailResult(
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getPaymentProvider(),
                payment.getApprovedAt(),
                payment.getCanceledAt()
        );
    }
}