package com.tikitaka.paymentnotification.payment.infrastructure.pg;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayRequest;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway mockPaymentGateway =
            new MockPaymentGateway();

    @Test
    void 결제_승인에_성공한다() {
        // given
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "PAY-test-order",
                150000L,
                "KRW"
        );

        // when
        PaymentGatewayResult result =
                mockPaymentGateway.approve(request);

        // then
        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.SUCCESS);

        assertThat(result.pgPaymentKey())
                .isNotNull()
                .startsWith("MOCK-");

        assertThat(result.failureCode()).isNull();
        assertThat(result.failureReason()).isNull();
    }
}