package com.tikitaka.paymentnotification.payment.infrastructure.persistence.transaction;

import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentTransactionRepositoryImpl implements PaymentTransactionRepository {

    private final PaymentTransactionJpaRepository paymentTransactionJpaRepository;

    @Override
    public PaymentTransaction save(PaymentTransaction paymentTransaction) {
        return paymentTransactionJpaRepository.save(paymentTransaction);
    }

    @Override
    public Optional<PaymentTransaction> findLatestApproveTransaction(UUID paymentId) {
        return paymentTransactionJpaRepository
                .findTopByPayment_PaymentIdAndTransactionTypeOrderByAttemptNoDesc(
                        paymentId,
                        PaymentTransactionType.APPROVE
                );
    }
}
