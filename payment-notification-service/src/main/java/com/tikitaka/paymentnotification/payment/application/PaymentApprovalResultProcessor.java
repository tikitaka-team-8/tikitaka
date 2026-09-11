package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventSerializer;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentFailedEvent;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentSucceededEvent;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentApprovalResultProcessor {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentEventSerializer paymentEventSerializer;


    @Transactional
    public PaymentApproveResult process(
            UUID paymentId,
            PaymentGatewayResult result,
            OffsetDateTime requestedAt
    ) {
        Payment payment = findPayment(paymentId);

        switch (result.status()) {
            case SUCCESS ->
                    handleApproveSuccess(
                            payment,
                            result,
                            requestedAt
                    );

            case FAILED ->
                    handleApproveFailed(
                            payment,
                            result,
                            requestedAt
                    );

            case UNKNOWN ->
                    handleApproveUnknown(
                            payment,
                            requestedAt
                    );
        }

        return PaymentApproveResult.from(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(
            UUID paymentId,
            OffsetDateTime requestedAt
    ) {
        Payment payment = findPayment(paymentId);

        payment.markUnknown();

        paymentTransactionRepository.save(
                PaymentTransaction.createApproveUnknown(
                        payment,
                        payment.getPaymentProvider(),
                        payment.getAmount(),
                        1,
                        requestedAt
                )
        );
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentException(
                                PaymentErrorCode.PAYMENT_NOT_FOUND
                        )
                );
    }


    // 승인 + 성공 Outbox
    private void handleApproveSuccess(
            Payment payment,
            PaymentGatewayResult result,
            OffsetDateTime requestedAt
    ) {
        payment.approve(result.paymentMethod());

        paymentTransactionRepository.save(
                PaymentTransaction.createApproveSuccess(
                        payment,
                        payment.getPaymentProvider(),
                        payment.getPgPaymentKey(),
                        payment.getAmount(),
                        1,
                        requestedAt
                )
        );

        PaymentSucceededEvent event = PaymentSucceededEvent.from(payment);

        saveOutbox(
                payment,
                event.eventType(),
                paymentEventSerializer.serialize(event)
        );
    }

    // 실패 + Outbox
    private void handleApproveFailed(
            Payment payment,
            PaymentGatewayResult result,
            OffsetDateTime requestedAt
    ) {
        payment.fail(
                result.failureCode(),
                result.failureReason()
        );

        paymentTransactionRepository.save(
                PaymentTransaction.createApproveFailed(
                        payment,
                        payment.getPaymentProvider(),
                        payment.getAmount(),
                        1,
                        result.failureCode(),
                        result.failureReason(),
                        requestedAt
                )
        );

        PaymentFailedEvent event =
                PaymentFailedEvent.from(payment);

        saveOutbox(
                payment,
                event.eventType(),
                paymentEventSerializer.serialize(event)
        );
    }

    // Unknown + Outbox
    private void handleApproveUnknown(Payment payment, OffsetDateTime requestedAt) {
        payment.markUnknown();

        paymentTransactionRepository.save(
                PaymentTransaction.createApproveUnknown(
                        payment,
                        payment.getPaymentProvider(),
                        payment.getAmount(),
                        1,
                        requestedAt
                )
        );
    }

    // 아웃박스 저장
    private void saveOutbox(
            Payment payment,
            String eventType,
            String payload
    ) {
        PaymentOutbox outbox =
                PaymentOutbox.create(
                        payment,
                        eventType,
                        payload
                );

        paymentOutboxRepository.save(outbox);
    }
}
