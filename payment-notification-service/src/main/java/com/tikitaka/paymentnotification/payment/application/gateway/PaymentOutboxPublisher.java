package com.tikitaka.paymentnotification.payment.application.gateway;


import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentEventPublishException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentOutboxPublisher {

    private static final String PAYMENT_EVENT_TOPIC = "payment-events";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 3;

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    @Transactional
    public void publishPendingOutboxes(){
        List<PaymentOutbox> outboxes = paymentOutboxRepository.findPendingOutboxes(BATCH_SIZE);

        for(PaymentOutbox outbox : outboxes){publish(outbox);}
    }

private void publish(PaymentOutbox outbox){

        try {
            paymentEventPublisher.publish(
                    PAYMENT_EVENT_TOPIC,
                    outbox.getPayment().getReservationId(),
                    outbox.getPayload()
            );

            outbox.markPublished();
        }catch (PaymentEventPublishException e){
            outbox.recordPublishFailure(MAX_RETRY_COUNT);
        }
    }

}
