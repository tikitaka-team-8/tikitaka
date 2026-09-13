package com.tikitaka.paymentnotification.payment.domain.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentOutboxRepository {
    PaymentOutbox save(PaymentOutbox paymentOutbox);

    List<PaymentOutbox> findPendingOutboxes(int limit);

    Optional<PaymentOutbox> findById(UUID outboxId);
}
