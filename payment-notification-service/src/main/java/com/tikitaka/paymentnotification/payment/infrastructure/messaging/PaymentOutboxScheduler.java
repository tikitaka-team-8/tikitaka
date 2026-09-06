package com.tikitaka.paymentnotification.payment.infrastructure.messaging;


import com.tikitaka.paymentnotification.payment.application.PaymentOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOutboxScheduler {

    private final PaymentOutboxPublisher paymentOutboxPublisher;

    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay-ms:1000}")
    public void publishPendingOutboxes(){
        paymentOutboxPublisher.publishPendingOutboxes();
    }



}
