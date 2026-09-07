package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventSerializer;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.gateway.ReservationPaymentValidator;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentFailedEvent;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentSucceededEvent;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxStatus;
import com.tikitaka.paymentnotification.payment.domain.payment.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionStatus;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionType;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
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

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private PaymentEventSerializer paymentEventSerializer;

    @Mock
    private ReservationPaymentValidator reservationPaymentValidator;

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

    @Test
    void 결제_승인에_성공하면_결제상태와_거래이력이_성공으로_저장된다() {
        // given
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentGateway.approve(any()))
                .thenReturn(
                        PaymentGatewayResult.success("MOCK-success-key")
                );

        when(paymentEventSerializer.serialize(any(PaymentSucceededEvent.class)))
                .thenReturn("{\"eventType\":\"PAYMENT_SUCCEEDED\"}");

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

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository)
                .save(transactionCaptor.capture());

        PaymentTransaction transaction =
                transactionCaptor.getValue();

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.SUCCESS);

        assertThat(transaction.getTransactionType())
                .isEqualTo(PaymentTransactionType.APPROVE);

        ArgumentCaptor<PaymentOutbox> outboxCaptor =
                ArgumentCaptor.forClass(PaymentOutbox.class);

        verify(paymentOutboxRepository)
                .save(outboxCaptor.capture());

        PaymentOutbox outbox =
                outboxCaptor.getValue();

        assertThat(outbox.getEventType())
                .isEqualTo("PAYMENT_SUCCEEDED");

        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isZero();

        assertThat(outbox.getPayment())
                .isEqualTo(payment);
    }

    @Test
    void 결제_승인에_실패하면_결제상태_거래이력_Outbox가_실패로_저장된다() {
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

        when(paymentEventSerializer.serialize(any(PaymentFailedEvent.class)))
                .thenReturn("{\"eventType\":\"PAYMENT_FAILED\"}");

        // when
        paymentService.approvePayment(
                paymentId,
                PaymentMethod.CARD
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository)
                .save(transactionCaptor.capture());

        PaymentTransaction transaction =
                transactionCaptor.getValue();

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.FAILED);

        assertThat(transaction.getFailureCode())
                .isEqualTo("MOCK_FAILED");

        assertThat(transaction.getFailureReason())
                .isEqualTo("Mock PG 결제 승인 실패");

        ArgumentCaptor<PaymentOutbox> outboxCaptor =
                ArgumentCaptor.forClass(PaymentOutbox.class);

        verify(paymentOutboxRepository)
                .save(outboxCaptor.capture());

        PaymentOutbox outbox =
                outboxCaptor.getValue();

        assertThat(outbox.getEventType())
                .isEqualTo("PAYMENT_FAILED");

        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isZero();

        assertThat(outbox.getPayment())
                .isEqualTo(payment);

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
        PaymentApproveResult result =
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

    @Test
    void 결제_금액이_예매_검증_금액과_다르면_결제를_승인하지_않는다() {
        // given
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

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
                        PaymentMethod.CARD
                )
        )
                .isInstanceOf(PaymentException.class);

        verify(paymentGateway, never()).approve(any());
        verify(paymentTransactionRepository, never()).save(any());
        verify(paymentOutboxRepository, never()).save(any());
    }
















}


