package com.tikitaka.ticketing.reservation.presentation.dto.response;

import com.tikitaka.ticketing.reservation.application.result.PaymentValidationResult;
import lombok.Getter;

import java.util.UUID;

@Getter
public class PaymentValidationResDto {

    private final UUID reservationId;
    private final Long userId;
    private final Long totalAmount;

    public PaymentValidationResDto(PaymentValidationResult result) {
        this.reservationId = result.getReservationId();
        this.userId = result.getUserId();
        this.totalAmount = result.getTotalAmount();
    }
}
