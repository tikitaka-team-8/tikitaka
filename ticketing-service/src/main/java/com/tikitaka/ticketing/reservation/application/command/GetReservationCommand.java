package com.tikitaka.ticketing.reservation.application.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class GetReservationCommand {

    private final Long loginUserId;
    private final String userRole;
    private final UUID reservationId;

    public GetReservationCommand(Long loginUserId, String userRole, UUID reservationId) {
        this.loginUserId = loginUserId;
        this.userRole = userRole;
        this.reservationId = reservationId;
    }
}
