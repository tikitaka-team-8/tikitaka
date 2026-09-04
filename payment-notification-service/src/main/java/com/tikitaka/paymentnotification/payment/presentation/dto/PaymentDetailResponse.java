package com.tikitaka.paymentnotification.payment.presentation.dto;

import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentDetailResponse (
        UUID paymentId,
        UUID reservationId,
        String orderId,
        Long amount,
        PaymentStatus status,
        String currency,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        OffsetDateTime approvedAt,
        OffsetDateTime canceledAt

) {

    public static PaymentDetailResponse from (PaymentDetailResult result){
        return new PaymentDetailResponse(
                result.paymentId(),
                result.reservationId(),
                result.orderId(),
                result.amount(),
                result.status(),
                result.currency(),
                result.paymentMethod(),
                result.paymentProvider(),
                result.approvedAt(),
                result.canceledAt()
        );
    }
}
