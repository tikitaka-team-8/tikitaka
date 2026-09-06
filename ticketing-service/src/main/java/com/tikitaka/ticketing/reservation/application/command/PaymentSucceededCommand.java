package com.tikitaka.ticketing.reservation.application.command;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class PaymentSucceededCommand {

    private final UUID eventId;
    private final UUID paymentId;
    private final UUID reservationId;
    private final Long userId;
    private final Long amount;
    private final Instant approvedAt;

    public PaymentSucceededCommand(UUID eventId, UUID paymentId, UUID reservationId, Long userId, Long amount,
            Instant approvedAt) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.approvedAt = approvedAt;
    }
}
