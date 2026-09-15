package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentUnknownReconciler {
    private final PaymentRepository paymentRepository;
    private final Optional<PaymentQueryGateway> paymentQueryGateway;
    private final PaymentApprovalResultProcessor paymentApprovalResultProcessor;

    @Transactional
    public PaymentApproveResult reconcile(UUID paymentId) {
        final Payment payment;

        try {
            payment = paymentRepository
                    .findUnknownByIdForUpdateNowait(paymentId)
                    .orElse(null);
        } catch (CannotAcquireLockException e) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_STATUS_CONFIRMATION_REQUIRED
            );
        }

        // UNKNOWN 조건에 맞는 row가 없다면
        // 다른 복구가 이미 완료됐을 수도 있으므로 현재 상태 재조회
        if (payment == null) {
            Payment currentPayment = paymentRepository.findById(paymentId)
                    .orElseThrow(() ->
                            new PaymentException(
                                    PaymentErrorCode.PAYMENT_NOT_FOUND
                            ));

            return PaymentApproveResult.from(currentPayment);
        }

        String paymentKey = payment.getPgPaymentKey();

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_STATUS_CONFIRMATION_REQUIRED);
        }

        PaymentQueryGateway queryGateway = paymentQueryGateway
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_STATUS_CONFIRMATION_REQUIRED));

        PaymentQueryResult queryResult = queryGateway.getPayment(paymentKey);

        validateQueriedPayment(payment, queryResult);

        return switch (queryResult.status()) {
            case "DONE" ->
                    paymentApprovalResultProcessor.recoverApproved(paymentId, queryResult);

            case "ABORTED", "EXPIRED" -> paymentApprovalResultProcessor.recoverFailed(paymentId, queryResult);

            default -> PaymentApproveResult.from(payment);
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