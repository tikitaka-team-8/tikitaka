package com.tikitaka.paymentnotification.payment.domain.event;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentFailedEvent (
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID aggregateId,
        Integer version,
        UUID paymentId,
        UUID reservationId,
        Long userId,
        String failureCode,
        OffsetDateTime failedAt

){
    private static final String EVENT_TYPE = "PAYMENT_FAILED";
    private static final int EVENT_VERSION = 1;

    public static PaymentFailedEvent from(Payment payment) {
        OffsetDateTime now = OffsetDateTime.now();

        return new PaymentFailedEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                now,
                payment.getReservationId(),
                EVENT_VERSION,
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getUserId(),
                payment.getFailureCode(),
                now
        );
    }
}