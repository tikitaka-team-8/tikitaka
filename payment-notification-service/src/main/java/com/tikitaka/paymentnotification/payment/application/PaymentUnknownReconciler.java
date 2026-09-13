package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentUnknownReconciler {
    private final PaymentRepository paymentRepository;
    private final PaymentQueryGateway paymentQueryGateway;
    private final PaymentApprovalResultProcessor paymentApprovalResultProcessor;

    public PaymentApproveResult reconcile(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        return reconcile(payment);
    }


    public PaymentApproveResult reconcile(Payment payment) {

        String paymentKey = payment.getPgPaymentKey();

        if (paymentKey == null || paymentKey.isBlank()) {
            return PaymentApproveResult.from(payment);
        }

        PaymentQueryResult queryResult = paymentQueryGateway.getPayment(paymentKey);

        validateQueriedPayment(payment, queryResult);

        return switch (queryResult.status()) {

            case "DONE" ->
                    paymentApprovalResultProcessor.recoverApproved(
                            payment.getPaymentId(),
                            queryResult
                    );

            case "ABORTED", "EXPIRED" ->
                    paymentApprovalResultProcessor.recoverFailed(
                            payment.getPaymentId(),
                            queryResult
                    );

            default ->
                    PaymentApproveResult.from(payment);
        };
    }

    private void validateQueriedPayment(Payment payment, PaymentQueryResult queryResult) {
        boolean mismatched =
                !Objects.equals(
                        payment.getPgPaymentKey(),
                        queryResult.paymentKey()
                )
                        || !Objects.equals(
                        payment.getOrderId(),
                        queryResult.orderId()
                )
                        || !Objects.equals(
                        payment.getAmount(),
                        queryResult.totalAmount()
                );

        if (mismatched) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_NOT_ALLOWED
            );
        }
    }
}