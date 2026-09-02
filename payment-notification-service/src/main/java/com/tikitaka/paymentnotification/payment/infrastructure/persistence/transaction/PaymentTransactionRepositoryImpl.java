package com.tikitaka.paymentnotification.payment.infrastructure.persistence.transaction;

import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentTransactionRepositoryImpl implements PaymentTransactionRepository {

    private final PaymentTransactionJpaRepository paymentTransactionJpaRepository;

    @Override
    public PaymentTransaction save(PaymentTransaction paymentTransaction) {
        return paymentTransactionJpaRepository.save(paymentTransaction);
    }
}
