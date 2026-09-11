package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        String method
) {
}