package com.tikitaka.paymentnotification.payment.domain.payment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByReservationId(UUID reservationId);

    boolean tryStartProcessing(UUID paymentId);

    boolean tryRestoreReady(UUID paymentId);

    List<Payment> findStaleProcessingPayments(OffsetDateTime threshold, int limit);

    Optional<Payment> findUnknownByIdForUpdateNowait(UUID paymentId);
}
