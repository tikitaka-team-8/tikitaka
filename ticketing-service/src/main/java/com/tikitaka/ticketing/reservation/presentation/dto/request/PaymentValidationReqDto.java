package com.tikitaka.ticketing.reservation.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentValidationReqDto {

    @NotNull
    @Positive
    private Long userId;
}
