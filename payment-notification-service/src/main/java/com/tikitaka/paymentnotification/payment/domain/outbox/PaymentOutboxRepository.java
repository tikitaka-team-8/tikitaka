package com.tikitaka.paymentnotification.payment.domain.outbox;

import java.util.List;

public interface PaymentOutboxRepository {
    PaymentOutbox save(PaymentOutbox paymentOutbox);

    List<PaymentOutbox> findPendingOutboxes(int limit);
}
