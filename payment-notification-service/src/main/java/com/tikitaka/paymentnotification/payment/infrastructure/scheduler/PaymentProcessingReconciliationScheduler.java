package com.tikitaka.paymentnotification.payment.infrastructure.scheduler;

import com.tikitaka.paymentnotification.payment.application.PaymentProcessingReconciler;
import com.tikitaka.paymentnotification.payment.application.PaymentUnknownReconciler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessingReconciliationScheduler {

    private final PaymentProcessingReconciler paymentProcessingReconciler;
    private final PaymentUnknownReconciler paymentUnknownReconciler;

    @Scheduled(fixedDelayString = "${payment.reconciliation.processing-delay-ms:60000}")
    public void reconcileStaleProcessingPayments() {
        List<UUID> paymentIds = paymentProcessingReconciler.markStaleProcessingPaymentsAsUnknown();

        for (UUID paymentId : paymentIds) {
            try {
                paymentUnknownReconciler.reconcile(paymentId);
            } catch (RuntimeException e) {
                log.error(
                        "결제 reconciliation에 실패했습니다. paymentId={}",
                        paymentId,
                        e
                );
            }
        }
    }
}