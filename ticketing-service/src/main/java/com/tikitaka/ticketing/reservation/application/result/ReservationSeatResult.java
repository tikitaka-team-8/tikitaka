package com.tikitaka.ticketing.reservation.application.result;

import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatInfo;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ReservationSeatResult {

    private final UUID scheduleSeatId;
    private final String section;
    private final String rowLabel;
    private final String seatNumber;
    private final String seatGrade;
    private final Long price;

    public ReservationSeatResult(ReservationSeatInfo seatDetail) {
        this.scheduleSeatId = seatDetail.scheduleSeatId();
        this.section = seatDetail.section();
        this.rowLabel = seatDetail.rowLabel();
        this.seatNumber = seatDetail.seatNumber();
        this.seatGrade = seatDetail.seatGrade();
        this.price = seatDetail.price();
    }
}
