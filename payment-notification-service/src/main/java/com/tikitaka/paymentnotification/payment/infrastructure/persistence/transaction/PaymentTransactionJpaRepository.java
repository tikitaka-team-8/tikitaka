package com.tikitaka.paymentnotification.payment.infrastructure.persistence.transaction;


import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransaction, UUID> {

}
