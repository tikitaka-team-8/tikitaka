package com.tikitaka.ticketing.reservation.domain.port;

import com.tikitaka.ticketing.reservation.domain.model.PaymentCreationInfo;

import java.util.UUID;

public interface PaymentCreationPort {

    PaymentCreationInfo createPayment(UUID reservationId, Long userId, Long totalAmount, String idempotencyKey);
}
