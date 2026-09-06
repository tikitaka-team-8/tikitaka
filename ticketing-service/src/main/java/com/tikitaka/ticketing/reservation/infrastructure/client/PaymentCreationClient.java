package com.tikitaka.ticketing.reservation.infrastructure.client;

import com.tikitaka.ticketing.reservation.domain.model.PaymentCreationInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "paymentCreationClient",
        url = "${clients.payment-notification-service.url}"
)
public interface PaymentCreationClient {

    @PostMapping("/api/v1/internal/payments")
    PaymentCreationInfo createPayment(@RequestHeader("X-Service-Key") String serviceKey,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody PaymentCreationRequest request);
}
