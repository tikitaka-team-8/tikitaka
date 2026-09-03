package com.tikitaka.paymentnotification.payment.presentation.dto;

import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentApproveRequest (
        @NotNull
        PaymentMethod paymentMethod
){

}
