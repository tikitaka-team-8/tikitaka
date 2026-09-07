package com.tikitaka.ticketing.reservation.presentation.dto.response;

import com.tikitaka.ticketing.reservation.application.result.CreateReservationResult;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class CreateReservationResDto {

    private final UUID reservationId;
    private final String reservationNumber;
    private final UUID paymentId;
    private final ReservationStatus reservationStatus;
    private final Integer seatCount;
    private final Long totalAmount;
    private final Instant createdAt;

    public CreateReservationResDto(CreateReservationResult result) {
        this.reservationId = result.getReservationId();
        this.reservationNumber = result.getReservationNumber();
        this.paymentId = result.getPaymentId();
        this.reservationStatus = result.getReservationStatus();
        this.seatCount = result.getSeatCount();
        this.totalAmount = result.getTotalAmount();
        this.createdAt = result.getCreatedAt();
    }
}
