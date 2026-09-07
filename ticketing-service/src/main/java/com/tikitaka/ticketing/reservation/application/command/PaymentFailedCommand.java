package com.tikitaka.ticketing.reservation.application.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PaymentFailedCommand {

    private final UUID eventId;
    private final UUID paymentId;
    private final UUID reservationId;
    private final Long userId;
    private final Long amount;

    public PaymentFailedCommand(UUID eventId, UUID paymentId, UUID reservationId, Long userId, Long amount) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
    }
}
