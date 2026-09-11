package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss;

import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossConfirmRequest;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossPaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "tossPaymentsClient",
        url = "${payment.toss.base-url}"
)
public interface TossPaymentsFeignClient {

    @PostMapping("/v1/payments/confirm")
    TossPaymentResponse confirm(@RequestHeader("Authorization") String authorization,
                                @RequestBody TossConfirmRequest request);

}
