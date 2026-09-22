package com.tikitaka.paymentnotification.payment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "결제 승인 요청")
public record PaymentApproveRequest (
        String paymentKey
){

}
