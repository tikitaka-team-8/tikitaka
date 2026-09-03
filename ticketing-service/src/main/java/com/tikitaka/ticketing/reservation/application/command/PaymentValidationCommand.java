package com.tikitaka.ticketing.reservation.application.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PaymentValidationCommand {

    private final UUID reservationId;
    private final Long userId;

    public PaymentValidationCommand(UUID reservationId, Long userId) {
        this.reservationId = reservationId;
        this.userId = userId;
    }
}
