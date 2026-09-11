package com.tikitaka.paymentnotification.payment.application.gateway;

public record PaymentGatewayRequest(
        String paymentKey,
        String orderId,
        Long amount
){
}
