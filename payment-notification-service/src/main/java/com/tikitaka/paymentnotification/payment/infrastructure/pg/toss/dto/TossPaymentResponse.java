package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        Long totalAmount,
        String status,
        String method
) {
}