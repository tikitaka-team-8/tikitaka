package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto;

public record TossErrorResponse(
        String code,
        String message
) {
}
