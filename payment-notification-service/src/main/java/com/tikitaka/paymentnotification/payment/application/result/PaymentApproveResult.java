package com.tikitaka.paymentnotification.payment.application.result;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;

import java.util.UUID;

public record PaymentApproveResult(
        UUID paymentId,
        PaymentStatus status,
        String failureCode,
        String failureReason
) {
    public static PaymentApproveResult from(Payment payment) {
        return new PaymentApproveResult(
                payment.getPaymentId(),
                payment.getStatus(),
                payment.getFailureCode(),
                payment.getFailureReason()
        );
    }
}