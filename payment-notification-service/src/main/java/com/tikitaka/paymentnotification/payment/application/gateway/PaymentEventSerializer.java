package com.tikitaka.paymentnotification.payment.application.gateway;

public interface PaymentEventSerializer {

    String serialize(Object event);
}
