package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.domain.payment.*;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionStatus;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID paymentId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();

        payment = Payment.create(
                UUID.randomUUID(),
                1L,
                "PAY-test-order",
                "test-idempotency-key",
                150000L,
                "KRW",
                PaymentProvider.MOCK
        );
    }

    @Test
    void 결제_승인에_성공하면_결제상태와_거래이력이_성공으로_저장된다() {
        // given
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentGateway.approve(any()))
                .thenReturn(
                        PaymentGatewayResult.success("MOCK-success-key")
                );

        // when
        paymentService.approvePayment(
                paymentId,
                PaymentMethod.CARD
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.APPROVED);

        assertThat(payment.getPaymentMethod())
                .isEqualTo(PaymentMethod.CARD);

        ArgumentCaptor<PaymentTransaction> captor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository).save(captor.capture());

        PaymentTransaction transaction = captor.getValue();

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.SUCCESS);

        assertThat(transaction.getTransactionType())
                .isEqualTo(PaymentTransactionType.APPROVE);
    }

    @Test
    void 결제_승인에_실패하면_결제상태와_거래이력이_실패로_저장된다() {
        // given
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentGateway.approve(any()))
                .thenReturn(
                        PaymentGatewayResult.failed(
                                "MOCK_FAILED",
                                "Mock PG 결제 승인 실패"
                        )
                );

        // when
        paymentService.approvePayment(
                paymentId,
                PaymentMethod.CARD
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentTransaction> captor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository).save(captor.capture());

        PaymentTransaction transaction = captor.getValue();

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.FAILED);

        assertThat(transaction.getFailureCode())
                .isEqualTo("MOCK_FAILED");

        assertThat(transaction.getFailureReason())
                .isEqualTo("Mock PG 결제 승인 실패");
    }


    @Test
    void 결제_승인_결과를_확인할_수_없으면_결제상태와_거래이력이_UNKNOWN으로_저장된다() {
        // given
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentGateway.approve(any()))
                .thenReturn(
                        PaymentGatewayResult.unknown()
                );

        // when
        paymentService.approvePayment(
                paymentId,
                PaymentMethod.CARD
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        ArgumentCaptor<PaymentTransaction> captor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository).save(captor.capture());

        PaymentTransaction transaction = captor.getValue();

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.UNKNOWN);
    }
}