package com.tikitaka.paymentnotification.payment.application.gateway;

public interface PaymentQueryGateway {

    PaymentQueryResult getPayment(String paymentKey);
}