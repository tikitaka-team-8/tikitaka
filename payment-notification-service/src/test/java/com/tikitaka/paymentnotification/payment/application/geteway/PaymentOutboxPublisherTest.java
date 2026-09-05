package com.tikitaka.paymentnotification.payment.application.geteway;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventPublisher;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentOutboxPublisher;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxStatus;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.exception.PaymentEventPublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


import java.util.List;
import java.util.UUID;

class PaymentOutboxPublisherTest {

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private PaymentOutboxPublisher paymentOutboxPublisher;

    private Payment payment;
    private PaymentOutbox outbox;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        payment = Payment.create(
                UUID.randomUUID(),
                1L,
                "PAY-test-order",
                "test-idempotency-key",
                150000L,
                PaymentProvider.MOCK
        );

        outbox = PaymentOutbox.create(
                payment,
                "PAYMENT_SUCCEEDED",
                "{\"eventType\":\"PAYMENT_SUCCEEDED\"}"
        );
    }

    @Test
    void Outbox_발행에_성공하면_PUBLISHED_상태가_된다() {
        // given
        when(paymentOutboxRepository.findPendingOutboxes(100))
                .thenReturn(List.of(outbox));

        // when
        paymentOutboxPublisher.publishPendingOutboxes();

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.PUBLISHED);

        assertThat(outbox.getPublishedAt())
                .isNotNull();

        verify(paymentEventPublisher).publish(
                eq("payment-events"),
                eq(payment.getReservationId()),
                eq(outbox.getPayload())
        );
    }

    @Test
    void Kafka_발행에_실패하면_Outbox는_PENDING_상태로_재시도_횟수가_증가한다() {
        // given
        when(paymentOutboxRepository.findPendingOutboxes(100))
                .thenReturn(List.of(outbox));

        doThrow(
                new PaymentEventPublishException(
                        "Payment 이벤트 Kafka 발행에 실패했습니다.",
                        new RuntimeException()
                )
        )
                .when(paymentEventPublisher)
                .publish(anyString(), any(UUID.class), anyString());

        // when
        paymentOutboxPublisher.publishPendingOutboxes();

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isEqualTo(1);
    }

    @Test
    void Kafka_발행이_최대_재시도_횟수만큼_실패하면_FAILED_상태가_된다() {
        // given
        when(paymentOutboxRepository.findPendingOutboxes(100))
                .thenReturn(List.of(outbox));

        doThrow(
                new PaymentEventPublishException(
                        "Payment 이벤트 Kafka 발행에 실패했습니다.",
                        new RuntimeException()
                )
        )
                .when(paymentEventPublisher)
                .publish(anyString(), any(UUID.class), anyString());

        // when
        paymentOutboxPublisher.publishPendingOutboxes();
        paymentOutboxPublisher.publishPendingOutboxes();
        paymentOutboxPublisher.publishPendingOutboxes();

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.FAILED);

        assertThat(outbox.getRetryCount())
                .isEqualTo(3);
    }

    @Test
    void Kafka_발행시_reservationId를_key로_사용한다() {
        // given
        when(paymentOutboxRepository.findPendingOutboxes(100))
                .thenReturn(List.of(outbox));

        // when
        paymentOutboxPublisher.publishPendingOutboxes();

        // then
        verify(paymentEventPublisher).publish(
                eq("payment-events"),
                eq(payment.getReservationId()),
                eq(outbox.getPayload())
        );
    }
}