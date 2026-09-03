package com.tikitaka.ticketing.reservation.application.result;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import lombok.Getter;

import java.util.UUID;

@Getter
public class PaymentValidationResult {

    private final UUID reservationId;
    private final Long userId;
    private final Long totalAmount;

    public PaymentValidationResult(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.userId = reservation.getUserId();
        this.totalAmount = reservation.getTotalAmount();
    }
}
