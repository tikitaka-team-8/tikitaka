package com.tikitaka.paymentnotification.payment.infrastructure.reservation;

import com.tikitaka.paymentnotification.payment.infrastructure.persistence.reservation.ReservationPaymentValidationRequest;
import com.tikitaka.paymentnotification.payment.infrastructure.persistence.reservation.ReservationPaymentValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(
        name = "ticketing-service",
        url = "${service.ticketing.url}"
)

public interface ReservationFeignClient {
    @PostMapping("/api/v1/internal/reservations/{reservationId}/payment-validation")
    ReservationPaymentValidationResponse validatePayment(
            @RequestHeader("X-Service-Key") String serviceKey,
            @PathVariable UUID reservationId,
            @RequestBody ReservationPaymentValidationRequest request
            );

}
