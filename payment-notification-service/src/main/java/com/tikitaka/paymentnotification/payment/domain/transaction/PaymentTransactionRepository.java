package com.tikitaka.paymentnotification.payment.domain.transaction;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository {
    PaymentTransaction save(PaymentTransaction paymentTransaction);

    Optional<PaymentTransaction> findLatestApproveTransaction(UUID paymentId);


}
