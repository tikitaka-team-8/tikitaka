package com.tikitaka.paymentnotification.payment.application.command;

import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;

import java.util.UUID;

public record PaymentCreateCommand (
        UUID reservationId,
        Long userId,
        String idempotencyKey,
        Long amount,
        String currency,
        PaymentProvider paymentProvider
){ }
