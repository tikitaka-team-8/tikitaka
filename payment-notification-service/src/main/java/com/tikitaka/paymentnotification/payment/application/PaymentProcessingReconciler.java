package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentProcessingReconciler {
    private static final int BATCH_SIZE = 100;

    private final PaymentRepository paymentRepository;

    @Value("${payment.reconciliation.processing-timeout-minutes:5}")
    private long processingTimeoutMinutes;

    @Transactional
    public List<UUID> markStaleProcessingPaymentsAsUnknown() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(processingTimeoutMinutes);

        List<Payment> payments = paymentRepository.findStaleProcessingPayments(threshold, BATCH_SIZE);

        List<UUID> paymentIds = new ArrayList<>();

        for (Payment payment : payments) {
            payment.markUnknown();
            paymentIds.add(payment.getPaymentId());
        }

        return paymentIds;
    }
}