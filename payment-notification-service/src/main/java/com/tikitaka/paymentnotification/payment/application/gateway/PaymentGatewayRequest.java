package com.tikitaka.paymentnotification.payment.application.gateway;

public record PaymentGatewayRequest(
        String orderId,
        Long amount,
        String currency
){
}
