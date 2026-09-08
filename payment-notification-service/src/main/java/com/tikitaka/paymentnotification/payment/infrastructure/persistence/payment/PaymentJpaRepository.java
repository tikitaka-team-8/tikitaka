package com.tikitaka.paymentnotification.payment.infrastructure.persistence.payment;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByReservationId(UUID reservationId);

    //

    @Modifying
    @Query("""
        UPDATE Payment p
           SET p.status =
               com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus.PROCESSING,
               p.updatedAt = CURRENT_TIMESTAMP
         WHERE p.paymentId = :paymentId
           AND p.status =
               com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus.READY
        """)
    int tryStartProcessing(@Param("paymentId") UUID paymentId);


    @Modifying
    @Query("""
        UPDATE Payment p
           SET p.status =
               com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus.READY,
               p.updatedAt = CURRENT_TIMESTAMP
         WHERE p.paymentId = :paymentId
           AND p.status =
               com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus.PROCESSING
        """)
    int tryRestoreReady(@Param("paymentId") UUID paymentId);
}
