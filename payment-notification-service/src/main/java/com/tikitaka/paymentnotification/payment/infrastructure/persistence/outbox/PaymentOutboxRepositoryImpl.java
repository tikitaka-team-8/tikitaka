package com.tikitaka.paymentnotification.payment.infrastructure.persistence.outbox;

import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentOutboxRepositoryImpl implements PaymentOutboxRepository {

    private final PaymentOutboxJpaRepository paymentOutboxJpaRepository;

    @Override
    public PaymentOutbox save(PaymentOutbox paymentOutbox) {
        return paymentOutboxJpaRepository.save(paymentOutbox);
    }

    @Override
    public List<PaymentOutbox> findPendingOutboxes(int limit) {
        return paymentOutboxJpaRepository
                .findPendingOutboxesForUpdate(limit);
    }
}
