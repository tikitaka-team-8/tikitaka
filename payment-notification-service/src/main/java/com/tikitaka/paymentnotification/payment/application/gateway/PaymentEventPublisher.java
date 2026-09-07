package com.tikitaka.paymentnotification.payment.application.gateway;

import java.util.UUID;

public interface PaymentEventPublisher {

    void publish(
            String topic,
            UUID key,
            String payload
    );
}
