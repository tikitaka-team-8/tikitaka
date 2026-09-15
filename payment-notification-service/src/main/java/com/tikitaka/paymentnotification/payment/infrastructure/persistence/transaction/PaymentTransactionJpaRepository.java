package com.tikitaka.paymentnotification.payment.infrastructure.persistence.transaction;


import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction>
    findTopByPayment_PaymentIdAndTransactionTypeOrderByAttemptNoDesc(UUID paymentId, PaymentTransactionType transactionType);
}
