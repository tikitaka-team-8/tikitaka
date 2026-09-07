package com.tikitaka.paymentnotification.payment.infrastructure.persistence.payment;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByReservationId(UUID reservationId);
}
