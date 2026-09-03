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

    @Test
    void 결제_승인에_실패한다() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "PAY-FAIL-test",
                150000L,
                "KRW"
        );

        PaymentGatewayResult result =
                mockPaymentGateway.approve(request);

        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.FAILED);

        assertThat(result.failureCode())
                .isEqualTo("MOCK_FAILED");

        assertThat(result.failureReason())
                .isEqualTo("Mock PG 결제 승인 실패");
    }

    @Test
    void 결제_승인_결과를_확인할_수_없다() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "PAY-UNKNOWN-test",
                150000L,
                "KRW"
        );

        PaymentGatewayResult result =
                mockPaymentGateway.approve(request);

        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.UNKNOWN);

        assertThat(result.pgPaymentKey()).isNull();
        assertThat(result.failureCode()).isNull();
        assertThat(result.failureReason()).isNull();
    }

}