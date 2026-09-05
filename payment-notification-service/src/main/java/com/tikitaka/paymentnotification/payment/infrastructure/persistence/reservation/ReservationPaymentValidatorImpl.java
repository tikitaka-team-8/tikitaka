package com.tikitaka.paymentnotification.payment.infrastructure.persistence.reservation;

import com.tikitaka.paymentnotification.payment.application.gateway.ReservationPaymentValidator;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.infrastructure.reservation.ReservationFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationPaymentValidatorImpl implements ReservationPaymentValidator {

    private final ReservationFeignClient reservationFeignClient;

    @Value("${internal.ticketing-service-key}")
    private String ticketingServiceKey;

    @Override
    public ReservationPaymentValidationResult validate(UUID reservationId, Long userId) {

        ReservationPaymentValidationRequest request =
                new ReservationPaymentValidationRequest(userId);


        ReservationPaymentValidationResponse response =
                reservationFeignClient.validatePayment(
                        ticketingServiceKey,
                        reservationId,
                        request
                );

        return new ReservationPaymentValidationResult(
                response.reservationId(),
                response.userId(),
                response.totalAmount()
        );
    }
}
