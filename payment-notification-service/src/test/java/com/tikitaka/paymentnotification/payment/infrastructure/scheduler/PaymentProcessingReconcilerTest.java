package com.tikitaka.paymentnotification.payment.infrastructure.scheduler;

import com.tikitaka.paymentnotification.payment.application.PaymentProcessingReconciler;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingReconcilerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentProcessingReconciler paymentProcessingReconciler;

    @Test
    void 오래된_PROCESSING_결제를_UNKNOWN으로_전환한다() {
        // given
        ReflectionTestUtils.setField(
                paymentProcessingReconciler,
                "processingTimeoutMinutes",
                5L
        );

        Payment payment = mock(Payment.class);
        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findStaleProcessingPayments(
                any(OffsetDateTime.class),
                eq(100)
        )).thenReturn(List.of(payment));

        when(payment.getPaymentId())
                .thenReturn(paymentId);

        // when
        List<UUID> paymentIds =
                paymentProcessingReconciler
                        .markStaleProcessingPaymentsAsUnknown();

        // then
        verify(payment).markUnknown();

        assertThat(paymentIds)
                .containsExactly(paymentId);
    }
}