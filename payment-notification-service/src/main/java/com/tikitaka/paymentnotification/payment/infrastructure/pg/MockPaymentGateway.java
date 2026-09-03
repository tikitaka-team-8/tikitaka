package com.tikitaka.paymentnotification.payment.infrastructure.pg;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayRequest;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {


    @Override
    public PaymentGatewayResult approve(PaymentGatewayRequest request) {

        if (request.orderId().contains("FAIL")) {
            return PaymentGatewayResult.failed(
                    "MOCK_FAILED",
                    "Mock PG 결제 승인 실패"
            );
        }

        if (request.orderId().contains("UNKNOWN")) {
            return PaymentGatewayResult.unknown();}

        return PaymentGatewayResult.success("MOCK-" + UUID.randomUUID());
    }
}
