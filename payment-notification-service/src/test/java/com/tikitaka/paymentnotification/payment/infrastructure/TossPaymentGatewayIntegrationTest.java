package com.tikitaka.paymentnotification.payment.infrastructure;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
@Disabled("실제 Toss API 및 로컬 환경변수가 필요한 수동 통합 테스트")
@SpringBootTest
@ActiveProfiles("local")
class TossPaymentGatewayIntegrationTest {

    @Autowired
    private PaymentQueryGateway paymentQueryGateway;

    @Test
    void 실제_Toss_결제를_조회한다() {

        // given
        String paymentKey = "tviva20260911151910NY0N9";

        // when
        PaymentQueryResult result =
                paymentQueryGateway.getPayment(paymentKey);

        // then
        System.out.println("paymentKey = " + result.paymentKey());
        System.out.println("orderId = " + result.orderId());
        System.out.println("totalAmount = " + result.totalAmount());
        System.out.println("status = " + result.status());
        System.out.println("paymentMethod = " + result.paymentMethod());

        assertThat(result.paymentKey()).isEqualTo(paymentKey);
        assertThat(result.status()).isEqualTo("DONE");
    }
}