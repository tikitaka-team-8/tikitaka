package com.tikitaka.ticketing.seat.presentation.dto.response;


import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.enums.SeatStatus;

import java.util.UUID;


public record ScheduleSeatResponse(
        UUID scheduleSeatId,
        String section,
        String rowLabel,
        String seatNumber,
        String seatGrade,
        Long price,
        SeatStatus seatStatus
) {

    public static ScheduleSeatResponse from(ScheduleSeat seat) {
        return new ScheduleSeatResponse(
                seat.getScheduleSeatId(),
                seat.getSection(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getSeatGrade(),
                seat.getPrice(),
                seat.getSeatStatus()
        );
    }
}
