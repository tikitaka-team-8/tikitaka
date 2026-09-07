package com.tikitaka.paymentnotification.payment.domain.event;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID aggregateId,
        Integer version,
        UUID paymentId,
        UUID reservationId,
        Long userId,
        Long amount,
        OffsetDateTime approvedAt
) {

    private static final String EVENT_TYPE = "PAYMENT_SUCCEEDED";
    private static final int EVENT_VERSION = 1;

    public static PaymentSucceededEvent from(Payment payment) {
        return new PaymentSucceededEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                OffsetDateTime.now(),
                payment.getReservationId(),
                EVENT_VERSION,
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getApprovedAt()
        );
    }
}