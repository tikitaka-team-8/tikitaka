package com.tikitaka.paymentnotification.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PaymentCreateRequest(

        @NotNull
        UUID reservationId,

        @NotNull
        Long userId, // 연동 전이라 넣어둠

        @NotNull
        @Positive
        Long totalAmount

) {
}