package com.tikitaka.ticketing.reservation.presentation.dto.response;

import com.tikitaka.ticketing.reservation.application.result.ReservationSeatResult;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ReservationSeatResDto {

    private final UUID scheduleSeatId;
    private final String section;
    private final String rowLabel;
    private final String seatNumber;
    private final String seatGrade;
    private final Long price;

    public ReservationSeatResDto(ReservationSeatResult result) {
        this.scheduleSeatId = result.getScheduleSeatId();
        this.section = result.getSection();
        this.rowLabel = result.getRowLabel();
        this.seatNumber = result.getSeatNumber();
        this.seatGrade = result.getSeatGrade();
        this.price = result.getPrice();
    }
}
