package com.tikitaka.paymentnotification.payment.infrastructure.pg;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayRequest;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.TossPaymentGateway;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.TossPaymentProperties;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.TossPaymentsFeignClient;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossConfirmRequest;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.TossErrorMapper;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossPaymentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class TossPaymentGatewayTest {

    @Mock
    private TossPaymentsFeignClient tossPaymentsFeignClient;

    @Mock
    private TossPaymentProperties tossPaymentProperties;

    @Mock
    private TossErrorMapper tossErrorMapper;

    @InjectMocks
    private TossPaymentGateway tossPaymentGateway;

    @Test
    void 카드_결제_승인에_성공한다() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "test-payment-key",
                "PAY-test-order",
                150000L
        );

        when(tossPaymentProperties.secretKey())
                .thenReturn("test_sk_test");

        when(tossPaymentsFeignClient.confirm(anyString(), any()))
                .thenReturn(
                        new TossPaymentResponse(
                                "test-payment-key",
                                "PAY-test-order",
                                150000L,
                                "DONE",
                                "카드"
                        )
                );

        PaymentGatewayResult result =
                tossPaymentGateway.approve(request);

        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.SUCCESS);

        assertThat(result.pgPaymentKey())
                .isEqualTo("test-payment-key");

        assertThat(result.paymentMethod())
                .isEqualTo(PaymentMethod.CARD);
    }

    @Test
    void 간편결제_승인에_성공한다() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "test-payment-key",
                "PAY-test-order",
                150000L
        );

        when(tossPaymentProperties.secretKey())
                .thenReturn("test_sk_test");

        when(tossPaymentsFeignClient.confirm(anyString(), any()))
                .thenReturn(
                        new TossPaymentResponse(
                                "test-payment-key",
                                "PAY-test-order",
                                150000L,
                                "DONE",
                                "간편결제"
                        )
                );

        PaymentGatewayResult result =
                tossPaymentGateway.approve(request);

        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.SUCCESS);

        assertThat(result.paymentMethod())
                .isEqualTo(PaymentMethod.EASY_PAY);
    }

    @Test
    void 예상하지_못한_Toss_상태는_UNKNOWN으로_처리한다() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "test-payment-key",
                "PAY-test-order",
                150000L
        );

        when(tossPaymentProperties.secretKey())
                .thenReturn("test_sk_test");

        when(tossPaymentsFeignClient.confirm(anyString(), any()))
                .thenReturn(
                        new TossPaymentResponse(
                                "test-payment-key",
                                "PAY-test-order",
                                150000L,
                                "WAITING_FOR_DEPOSIT",
                                "카드"
                        )
                );

        PaymentGatewayResult result =
                tossPaymentGateway.approve(request);

        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.UNKNOWN);

        assertThat(result.failureCode())
                .isEqualTo("TOSS_UNEXPECTED_STATUS");
    }

    @Test
    void 지원하지_않는_결제수단은_UNKNOWN으로_처리한다() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "test-payment-key",
                "PAY-test-order",
                150000L
        );

        when(tossPaymentProperties.secretKey())
                .thenReturn("test_sk_test");

        when(tossPaymentsFeignClient.confirm(anyString(), any()))
                .thenReturn(
                        new TossPaymentResponse(
                                "test-payment-key",
                                "PAY-test-order",
                                150000L,
                                "DONE",
                                "가상계좌"
                        )
                );

        PaymentGatewayResult result =
                tossPaymentGateway.approve(request);

        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.UNKNOWN);

        assertThat(result.failureCode())
                .isEqualTo("TOSS_UNSUPPORTED_PAYMENT_METHOD");
    }
}

