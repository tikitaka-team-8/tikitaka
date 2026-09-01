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
        return PaymentGatewayResult.success("MOCK-"+ UUID.randomUUID());
    }
}
