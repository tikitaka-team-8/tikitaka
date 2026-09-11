package com.tikitaka.paymentnotification.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentApproveRequest (
        @NotNull @NotBlank
        String paymentKey
){

}
