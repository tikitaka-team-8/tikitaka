package com.tikitaka.ticketing.reservation.application.result;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class CreateReservationResult {

    private final UUID reservationId;
    private final String reservationNumber;
    private final UUID paymentId;
    private final ReservationStatus reservationStatus;
    private final Integer seatCount;
    private final Long totalAmount;
    private final Instant createdAt;
    private final boolean created;

    public CreateReservationResult(Reservation reservation, boolean created) {
        this.reservationId = reservation.getReservationId();
        this.reservationNumber = reservation.getReservationNumber();
        this.paymentId = reservation.getPaymentId();
        this.reservationStatus = reservation.getReservationStatus();
        this.seatCount = reservation.getSeatCount();
        this.totalAmount = reservation.getTotalAmount();
        this.createdAt = reservation.getCreatedAt();
        this.created = created;
    }
}
