package com.tikitaka.paymentnotification.payment.domain.transaction;

public interface PaymentTransactionRepository {
    PaymentTransaction save(PaymentTransaction paymentTransaction);
}
