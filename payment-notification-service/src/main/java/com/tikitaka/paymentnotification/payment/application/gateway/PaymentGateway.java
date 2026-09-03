package com.tikitaka.paymentnotification.payment.application.gateway;

public interface PaymentGateway {

    PaymentGatewayResult approve(PaymentGatewayRequest request);
}
