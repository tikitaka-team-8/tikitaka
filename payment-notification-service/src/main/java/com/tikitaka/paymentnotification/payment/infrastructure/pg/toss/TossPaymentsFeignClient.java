package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss;

import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossConfirmRequest;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossPaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "tossPaymentsClient",
        url = "${payment.toss.base-url}"
)
public interface TossPaymentsFeignClient {

    @PostMapping("/v1/payments/confirm")
    TossPaymentResponse confirm(@RequestHeader("Authorization") String authorization,
                                @RequestBody TossConfirmRequest request);



    // 결제 상태 조회
    @GetMapping("/v1/payments/{paymentKey}")
    TossPaymentResponse getPayment(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String paymentKey
    );

}
