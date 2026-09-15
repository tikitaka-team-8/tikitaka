package com.tikitaka.paymentnotification.payment.application;


import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventPublisher;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxStatus;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentEventPublishException;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    @Transactional
    public void retryFailedOutbox(UUID outboxId) {
        PaymentOutbox outbox = paymentOutboxRepository.findById(outboxId)
                .orElseThrow(() ->
                        new PaymentException(PaymentErrorCode.PAYMENT_OUTBOX_NOT_FOUND));

        if (outbox.getStatus() != PaymentOutboxStatus.FAILED) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_OUTBOX_RETRY_NOT_ALLOWED);
        }

        outbox.retry();
    }

}
