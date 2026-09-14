package com.tikitaka.paymentnotification.payment.infrastructure.pg;

import com.tikitaka.paymentnotification.payment.application.gateway.*;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "payment.provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockPaymentGateway implements PaymentGateway, PaymentQueryGateway {


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

        return PaymentGatewayResult.success("MOCK-" + UUID.randomUUID(), PaymentMethod.CARD);
    }

    @Override
    public PaymentQueryResult getPayment(String paymentKey) {
        throw new PaymentException(
                PaymentErrorCode.PAYMENT_STATUS_CONFIRMATION_REQUIRED
        );
    }
}
