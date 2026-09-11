package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto;

public record TossConfirmRequest (
    String paymentKey,
    String orderId,
    Long amount
){}
