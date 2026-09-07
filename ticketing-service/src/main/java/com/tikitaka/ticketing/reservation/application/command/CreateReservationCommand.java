package com.tikitaka.ticketing.reservation.application.command;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class CreateReservationCommand {

    private final Long loginUserId;
    private final String userRole;
    private final String idempotencyKey;
    private final List<UUID> seatHoldIds;

    public CreateReservationCommand(Long loginUserId, String userRole, String idempotencyKey, List<UUID> seatHoldIds) {
        this.loginUserId = loginUserId;
        this.userRole = userRole;
        this.idempotencyKey = idempotencyKey;
        this.seatHoldIds = seatHoldIds;
    }
}
