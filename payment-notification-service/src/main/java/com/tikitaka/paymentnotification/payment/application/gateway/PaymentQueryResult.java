package com.tikitaka.paymentnotification.payment.application.gateway;

import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;

public record PaymentQueryResult(
        String paymentKey,
        String orderId,
        Long totalAmount,
        String status,
        PaymentMethod paymentMethod
) {
}