package com.tikitaka.paymentnotification.payment.presentation.dto;

import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;

import java.util.UUID;

public record PaymentApproveResponse(
        UUID paymentId,
        PaymentStatus status,
        String failureCode
) {

    public static PaymentApproveResponse from(PaymentApproveResult result) {
        return new PaymentApproveResponse(
                result.paymentId(),
                result.status(),
                result.failureCode()
        );
    }
}