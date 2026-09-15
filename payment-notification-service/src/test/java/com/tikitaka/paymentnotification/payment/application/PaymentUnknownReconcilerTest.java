package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentUnknownReconcilerTest {

    @Mock
    private PaymentQueryGateway paymentQueryGateway;

    @Mock
    private PaymentApprovalResultProcessor paymentApprovalResultProcessor;

    private PaymentUnknownReconciler paymentUnknownReconciler;

    @Mock
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentUnknownReconciler = new PaymentUnknownReconciler(
                paymentRepository,
                Optional.of(paymentQueryGateway),
                paymentApprovalResultProcessor
        );
    }


    @Test
    void UNKNOWN_결제가_Toss_DONE이면_APPROVED로_복구한다() {
        // given
        UUID paymentId = UUID.randomUUID();

        Payment payment = mock(Payment.class);
        PaymentQueryResult queryResult = new PaymentQueryResult(
                "payment-key",
                "order-id",
                150000L,
                "DONE",
                PaymentMethod.EASY_PAY
        );

        PaymentApproveResult expectedResult =
                mock(PaymentApproveResult.class);

        when(payment.getPaymentId()).thenReturn(paymentId);
        when(payment.getPgPaymentKey()).thenReturn("payment-key");
        when(payment.getOrderId()).thenReturn("order-id");
        when(payment.getAmount()).thenReturn(150000L);

        when(paymentQueryGateway.getPayment("payment-key"))
                .thenReturn(queryResult);

        when(paymentApprovalResultProcessor.recoverApproved(
                paymentId,
                queryResult
        )).thenReturn(expectedResult);

        // when
        PaymentApproveResult result =
                paymentUnknownReconciler.reconcile(payment);

        // then
        verify(paymentQueryGateway)
                .getPayment("payment-key");

        verify(paymentApprovalResultProcessor)
                .recoverApproved(paymentId, queryResult);

        assertThat(result).isSameAs(expectedResult);
    }
    @Test
    void UNKNOWN_결제에_paymentKey가_없으면_조회하지_않는다() {
        // given
        Payment payment = mock(Payment.class);
        PaymentApproveResult expectedResult = mock(PaymentApproveResult.class);

        when(payment.getPgPaymentKey()).thenReturn(null);

        try (MockedStatic<PaymentApproveResult> mocked =
                     mockStatic(PaymentApproveResult.class)) {

            mocked.when(() -> PaymentApproveResult.from(payment))
                    .thenReturn(expectedResult);

            // when
            PaymentApproveResult result =
                    paymentUnknownReconciler.reconcile(payment);

            // then
            verifyNoInteractions(paymentQueryGateway);
            verifyNoInteractions(paymentApprovalResultProcessor);

            assertThat(result).isSameAs(expectedResult);
        }
    }

    @Test
    void PG_조회_결과가_결제정보와_다르면_예외가_발생한다() {
        // given
        Payment payment = mock(Payment.class);

        when(payment.getPgPaymentKey()).thenReturn("payment-key");

        PaymentQueryResult queryResult =
                new PaymentQueryResult(
                        "different-payment-key",
                        "order-id",
                        150000L,
                        "DONE",
                        PaymentMethod.EASY_PAY
                );

        when(paymentQueryGateway.getPayment("payment-key"))
                .thenReturn(queryResult);

        // when & then
        assertThatThrownBy(() ->
                paymentUnknownReconciler.reconcile(payment)
        )
                .isInstanceOf(PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_ALLOWED);

        verifyNoInteractions(paymentApprovalResultProcessor);
    }

    @Test
    void UNKNOWN_결제가_Toss_ABORTED이면_FAILED로_복구한다() {
        // given
        UUID paymentId = UUID.randomUUID();

        Payment payment = mock(Payment.class);

        PaymentQueryResult queryResult =
                new PaymentQueryResult(
                        "payment-key",
                        "order-id",
                        150000L,
                        "ABORTED",
                        PaymentMethod.OTHER
                );

        PaymentApproveResult expectedResult =
                mock(PaymentApproveResult.class);

        when(payment.getPaymentId())
                .thenReturn(paymentId);

        when(payment.getPgPaymentKey())
                .thenReturn("payment-key");

        when(payment.getOrderId())
                .thenReturn("order-id");

        when(payment.getAmount())
                .thenReturn(150000L);

        when(paymentQueryGateway.getPayment("payment-key"))
                .thenReturn(queryResult);

        when(paymentApprovalResultProcessor.recoverFailed(
                paymentId,
                queryResult
        )).thenReturn(expectedResult);

        // when
        PaymentApproveResult result =
                paymentUnknownReconciler.reconcile(payment);

        // then
        verify(paymentQueryGateway)
                .getPayment("payment-key");

        verify(paymentApprovalResultProcessor)
                .recoverFailed(
                        paymentId,
                        queryResult
                );

        verify(paymentApprovalResultProcessor, never())
                .recoverApproved(any(), any());

        assertThat(result)
                .isSameAs(expectedResult);
    }


}