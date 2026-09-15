package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventSerializer;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentFailedEvent;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentSucceededEvent;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxStatus;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalResultProcessorTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private PaymentEventSerializer paymentEventSerializer;

    @InjectMocks
    private PaymentApprovalResultProcessor processor;

    private UUID paymentId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = Payment.create(
                UUID.randomUUID(),
                1L,
                "PAY-test-order",
                "test-idempotency-key",
                150000L,
                PaymentProvider.MOCK
        );

        // 실제 Payment 엔티티의 ID를 테스트에서도 그대로 사용
        paymentId = payment.getPaymentId();

        // Processor가 받는 Payment는 이미 CAS 선점이 끝난 상태
        payment.startProcessing();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));
    }

    @Test
    void 결제_승인에_성공하면_APPROVED_상태와_성공_거래이력_Outbox를_저장한다() {
        // given
        payment.bindPaymentKey("MOCK-success-key");

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.success(
                        "MOCK-success-key",
                        PaymentMethod.CARD
                );

        when(paymentEventSerializer.serialize(any(PaymentSucceededEvent.class)))
                .thenReturn("{\"eventType\":\"PAYMENT_SUCCEEDED\"}");

        // when
        PaymentApproveResult result = processor.process(
                paymentId,
                gatewayResult,
                OffsetDateTime.now()
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.APPROVED);

        assertThat(payment.getPaymentMethod())
                .isEqualTo(PaymentMethod.CARD);

        assertThat(payment.getPgPaymentKey())
                .isEqualTo("MOCK-success-key");

        assertThat(result.status())
                .isEqualTo(PaymentStatus.APPROVED);

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

        PaymentOutbox outbox = outboxCaptor.getValue();

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
    void 결제_승인에_실패하면_FAILED_상태와_실패_거래이력_Outbox를_저장한다() {
        // given
        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.failed(
                        "MOCK_FAILED",
                        "Mock PG 결제 승인 실패"
                );

        when(paymentEventSerializer.serialize(any(PaymentFailedEvent.class)))
                .thenReturn("{\"eventType\":\"PAYMENT_FAILED\"}");

        // when
        processor.process(
                paymentId,
                gatewayResult,
                OffsetDateTime.now()
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.getFailureCode())
                .isEqualTo("MOCK_FAILED");

        assertThat(payment.getFailureReason())
                .isEqualTo("Mock PG 결제 승인 실패");

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository)
                .save(transactionCaptor.capture());

        PaymentTransaction transaction =
                transactionCaptor.getValue();

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.FAILED);

        assertThat(transaction.getTransactionType())
                .isEqualTo(PaymentTransactionType.APPROVE);

        assertThat(transaction.getFailureCode())
                .isEqualTo("MOCK_FAILED");

        assertThat(transaction.getFailureReason())
                .isEqualTo("Mock PG 결제 승인 실패");

        ArgumentCaptor<PaymentOutbox> outboxCaptor =
                ArgumentCaptor.forClass(PaymentOutbox.class);

        verify(paymentOutboxRepository)
                .save(outboxCaptor.capture());

        PaymentOutbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getEventType())
                .isEqualTo("PAYMENT_FAILED");

        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.PENDING);
    }

    @Test
    void 결제_승인_결과가_UNKNOWN이면_UNKNOWN_상태와_거래이력만_저장한다() {
        // given
        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.unknown();

        // when
        processor.process(
                paymentId,
                gatewayResult,
                OffsetDateTime.now()
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository)
                .save(transactionCaptor.capture());

        PaymentTransaction transaction =
                transactionCaptor.getValue();

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.UNKNOWN);

        assertThat(transaction.getTransactionType())
                .isEqualTo(PaymentTransactionType.APPROVE);

        verify(paymentOutboxRepository, never())
                .save(any());

        verifyNoInteractions(paymentEventSerializer);
    }

    @Test
    void UNKNOWN_결제에_승인_거래이력이_없어도_DONE이면_APPROVED로_복구한다() {
        // given
        payment.bindPaymentKey("toss-payment-key");
        payment.markUnknown();

        PaymentQueryResult queryResult =
                new PaymentQueryResult(
                        "toss-payment-key",
                        payment.getOrderId(),
                        payment.getAmount(),
                        "DONE",
                        PaymentMethod.CARD
                );

        when(paymentTransactionRepository.findLatestApproveTransaction(paymentId))
                .thenReturn(Optional.empty());

        when(paymentEventSerializer.serialize(any(PaymentSucceededEvent.class)))
                .thenReturn("serialized-event");

        // when
        PaymentApproveResult result =
                processor.recoverApproved(
                        paymentId,
                        queryResult
                );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.APPROVED);

        assertThat(payment.getPaymentMethod())
                .isEqualTo(PaymentMethod.CARD);

        assertThat(result.status())
                .isEqualTo(PaymentStatus.APPROVED);

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository)
                .save(transactionCaptor.capture());

        PaymentTransaction transaction =
                transactionCaptor.getValue();

        assertThat(transaction.getTransactionType())
                .isEqualTo(PaymentTransactionType.APPROVE);

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.SUCCESS);

        assertThat(transaction.getPgTransactionId())
                .isEqualTo("toss-payment-key");

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
    }


    @Test
    void UNKNOWN_결제가_ABORTED로_확정되면_FAILED_상태와_실패_거래이력_Outbox를_저장한다() {
        // given
        payment.bindPaymentKey("toss-payment-key");
        payment.markUnknown();

        PaymentQueryResult queryResult =
                new PaymentQueryResult(
                        "toss-payment-key",
                        payment.getOrderId(),
                        payment.getAmount(),
                        "ABORTED",
                        PaymentMethod.OTHER
                );

        when(paymentTransactionRepository.findLatestApproveTransaction(paymentId))
                .thenReturn(Optional.empty());

        when(paymentEventSerializer.serialize(any(PaymentFailedEvent.class)))
                .thenReturn("serialized-event");

        // when
        PaymentApproveResult result =
                processor.recoverFailed(
                        paymentId,
                        queryResult
                );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        assertThat(result.status())
                .isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(paymentTransactionRepository)
                .save(transactionCaptor.capture());

        PaymentTransaction transaction =
                transactionCaptor.getValue();

        assertThat(transaction.getTransactionType())
                .isEqualTo(PaymentTransactionType.APPROVE);

        assertThat(transaction.getStatus())
                .isEqualTo(PaymentTransactionStatus.FAILED);

        assertThat(transaction.getFailureCode())
                .isEqualTo("TOSS_ABORTED");

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
    }


}