package com.tikitaka.ticketing.seat.application.command;

import java.util.List;
import java.util.UUID;

public record CreateScheduleSeatsCommand(
        UUID eventSessionId,
        UUID venueId,
        List<SeatItem> seats
) {
    public record SeatItem(
            UUID venueSeatId,
            String section,
            String rowLabel,
            String seatNumber,
            String grade,
            Long price
    ) {
    }
}
