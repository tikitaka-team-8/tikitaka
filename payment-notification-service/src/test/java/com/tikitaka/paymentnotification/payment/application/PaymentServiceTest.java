package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventSerializer;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.gateway.ReservationPaymentValidator;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.domain.payment.*;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private PaymentEventSerializer paymentEventSerializer;

    @Mock
    private ReservationPaymentValidator reservationPaymentValidator;

    @Mock
    private PaymentProcessingAcquirer paymentProcessingAcquirer;

    @Mock
    private PaymentProcessingCompensator paymentProcessingCompensator;

    @Mock
    private PaymentApprovalResultProcessor paymentApprovalResultProcessor;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentKeyBinder paymentKeyBinder;

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
                PaymentProvider.MOCK
        );
    }


    @Test
    void 결제_승인에_성공하면_PG_결과를_처리한다() {
        // given
        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.success("MOCK-success-key", PaymentMethod.CARD);

        PaymentApproveResult approveResult =
                PaymentApproveResult.from(payment);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentProcessingAcquirer.acquire(paymentId))
                .thenReturn(true);

        when(reservationPaymentValidator.validate(
                payment.getReservationId(),
                payment.getUserId()
        )).thenReturn(
                new ReservationPaymentValidationResult(
                        payment.getReservationId(),
                        payment.getUserId(),
                        payment.getAmount()
                )
        );

        when(paymentGateway.approve(any()))
                .thenReturn(gatewayResult);

        when(paymentApprovalResultProcessor.process(
                eq(paymentId),
                eq(gatewayResult),
                any()
        )).thenReturn(approveResult);

        // when
        PaymentApproveResult result =
                paymentService.approvePayment(
                        paymentId,
                        payment.getUserId(),
                        "test-payment-key"
                );

        // then
        assertThat(result).isEqualTo(approveResult);

        verify(paymentProcessingAcquirer)
                .acquire(paymentId);

        verify(paymentGateway)
                .approve(any());

        verify(paymentApprovalResultProcessor)
                .process(
                        eq(paymentId),
                        eq(gatewayResult),
                        any()
                );
    }

    @Test
    void PG_승인에_실패하면_실패_결과를_처리한다() {
        // given
        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.failed(
                        "MOCK_FAILED",
                        "Mock PG 결제 승인 실패"
                );

        PaymentApproveResult approveResult =
                PaymentApproveResult.from(payment);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentProcessingAcquirer.acquire(paymentId))
                .thenReturn(true);

        stubReservationValidation();

        when(paymentGateway.approve(any()))
                .thenReturn(gatewayResult);

        when(paymentApprovalResultProcessor.process(
                eq(paymentId),
                eq(gatewayResult),
                any()
        )).thenReturn(approveResult);

        // when
        PaymentApproveResult result =
                paymentService.approvePayment(
                        paymentId,
                        payment.getUserId(),
                        "test-payment-key"
                );

        // then
        assertThat(result).isEqualTo(approveResult);

        verify(paymentApprovalResultProcessor)
                .process(
                        eq(paymentId),
                        eq(gatewayResult),
                        any()
                );
    }


    @Test
    void PG_승인_결과가_UNKNOWN이면_UNKNOWN_결과를_처리한다() {
        // given
        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.unknown();

        PaymentApproveResult approveResult =
                PaymentApproveResult.from(payment);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentProcessingAcquirer.acquire(paymentId))
                .thenReturn(true);

        stubReservationValidation();

        when(paymentGateway.approve(any()))
                .thenReturn(gatewayResult);

        when(paymentApprovalResultProcessor.process(
                eq(paymentId),
                eq(gatewayResult),
                any()
        )).thenReturn(approveResult);

        // when
        PaymentApproveResult result =
                paymentService.approvePayment(
                        paymentId,
                        payment.getUserId(),
                        "test-payment-key"
                );

        // then
        assertThat(result).isEqualTo(approveResult);

        verify(paymentApprovalResultProcessor)
                .process(
                        eq(paymentId),
                        eq(gatewayResult),
                        any()
                );
    }

    @Test
    void 결제_금액이_예매_검증_금액과_다르면_READY로_복구하고_PG를_호출하지_않는다() {
        // given
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentProcessingAcquirer.acquire(paymentId))
                .thenReturn(true);

        when(reservationPaymentValidator.validate(
                payment.getReservationId(),
                payment.getUserId()
        )).thenReturn(
                new ReservationPaymentValidationResult(
                        payment.getReservationId(),
                        payment.getUserId(),
                        payment.getAmount() + 1000
                )
        );

        // when & then
        assertThatThrownBy(() ->
                paymentService.approvePayment(
                        paymentId,
                        payment.getUserId(),
                        "test-payment-key"
                )
        ).isInstanceOf(PaymentException.class);

        verify(paymentProcessingCompensator).restoreReady(paymentId);

        verify(paymentGateway, never()).approve(any());

        verify(paymentApprovalResultProcessor, never()).process(any(), any(), any());
    }


    @Test
    void 결제_PROCESSING_선점에_실패하면_PG를_호출하지_않는다() {
        // given
        Payment processingPayment = mock(Payment.class);

        when(paymentRepository.findById(paymentId))
                .thenReturn(
                        Optional.of(payment),
                        Optional.of(processingPayment)
                );

        when(paymentProcessingAcquirer.acquire(paymentId))
                .thenReturn(false);

        when(processingPayment.getStatus())
                .thenReturn(PaymentStatus.PROCESSING);

        // when & then
        assertThatThrownBy(() ->
                paymentService.approvePayment(
                        paymentId,
                        payment.getUserId(),
                        "test-payment-key"
                )
        ).isInstanceOf(PaymentException.class);

        verify(paymentGateway, never())
                .approve(any());

        verify(reservationPaymentValidator, never())
                .validate(any(), any());

        verify(paymentApprovalResultProcessor, never())
                .process(any(), any(), any());
    }

    @Test
    void 결제_소유자는_결제정보를_조회할_수_있다() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        var result = paymentService.getPaymentById(paymentId, payment.getUserId());

        assertThat(result.paymentId()).isEqualTo(payment.getPaymentId());
        assertThat(result.reservationId()).isEqualTo(payment.getReservationId());
    }

    @Test
    void 다른_사용자는_결제정보를_조회할_수_없다() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPaymentById(paymentId, 999L))
                .isInstanceOf(PaymentException.class)
                .satisfies(exception -> assertThat(((PaymentException) exception).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void 다른_사용자는_결제를_승인할_수_없다() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.approvePayment(paymentId, 999L,  "test-payment-key"))
                .isInstanceOf(PaymentException.class)
                .satisfies(exception -> assertThat(((PaymentException) exception).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(reservationPaymentValidator, paymentGateway,
                paymentTransactionRepository, paymentOutboxRepository, paymentEventSerializer);
    }

    private void stubReservationValidation() {
        when(reservationPaymentValidator.validate(
                payment.getReservationId(),
                payment.getUserId()
        )).thenReturn(
                new ReservationPaymentValidationResult(
                        payment.getReservationId(),
                        payment.getUserId(),
                        payment.getAmount()
                )
        );
    }
}


