package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.domain.model.PaymentCreationInfo;
import com.tikitaka.ticketing.reservation.domain.port.PaymentCreationPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import com.tikitaka.ticketing.reservation.infrastructure.client.PaymentCreationClient;
import com.tikitaka.ticketing.reservation.infrastructure.client.PaymentCreationRequest;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentCreationAdapter implements PaymentCreationPort {

    private final PaymentCreationClient paymentCreationClient;
    private final String internalServiceKey;

    public PaymentCreationAdapter(PaymentCreationClient paymentCreationClient, @Value("${clients.payment-notification-service.service-key}") String internalServiceKey) {
        this.paymentCreationClient = paymentCreationClient;
        this.internalServiceKey = internalServiceKey;
    }

    @Override
    public PaymentCreationInfo createPayment(UUID reservationId, Long userId, Long totalAmount, String idempotencyKey) {
        try {
            PaymentCreationRequest request = new PaymentCreationRequest(reservationId, userId, totalAmount);
            return paymentCreationClient.createPayment(internalServiceKey, idempotencyKey, request);
        }
        catch (RetryableException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT);
        }
        catch (FeignException exception) {
            throw new BusinessException(ReservationErrorCode.PAYMENT_CREATION_FAILED);
        }
    }
}
