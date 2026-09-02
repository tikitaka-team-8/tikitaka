package com.tikitaka.ticketing.reservation.application.command;

import lombok.Getter;
import org.springframework.data.domain.Pageable;

@Getter
public class SearchReservationsCommand {

    private final Long loginUserId;
    private final String userRole;
    private final String eventTitle;
    private final String reservationStatus;
    private final Pageable pageable;

    public SearchReservationsCommand(
            Long loginUserId,
            String userRole,
            String eventTitle,
            String reservationStatus,
            Pageable pageable
    ) {
        this.loginUserId = loginUserId;
        this.userRole = userRole;
        this.eventTitle = eventTitle;
        this.reservationStatus = reservationStatus;
        this.pageable = pageable;
    }
}
