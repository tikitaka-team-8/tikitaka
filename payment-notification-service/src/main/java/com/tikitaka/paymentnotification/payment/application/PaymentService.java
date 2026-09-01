package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.command.PaymentCreateCommand;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayRequest;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentCreateResult;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentTransactionRepository paymentTransactionRepository;


    @Transactional
    public PaymentCreateResult createPayment(PaymentCreateCommand command) {

        Payment existingPayment = paymentRepository.findByIdempotencyKey(command.idempotencyKey())
                .orElse(null);

        if (existingPayment != null) {
            if (!existingPayment.isSameRequest(
                    command.reservationId(),
                    command.userId(),
                    command.amount(),
                    command.currency(),
                    command.paymentProvider()
            )) {
                throw new PaymentException(PaymentErrorCode.DUPLICATE_PAYMENT_REQUEST);
            }
            return PaymentCreateResult.from(existingPayment);
        }

        //주문번호 생성
        String orderId = createOrderId();


        Payment payment = Payment.create(
                command.reservationId(),
                command.userId(),
                orderId,
                command.idempotencyKey(),
                command.amount(),
                command.currency(),
                command.paymentProvider()
        );
        Payment savedPayment = paymentRepository.save(payment);

        return PaymentCreateResult.from(savedPayment);
    }


    @Transactional
    public void approvePayment(
            UUID paymentId,
            PaymentMethod paymentMethod
    ) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(
                        PaymentErrorCode.PAYMENT_NOT_FOUND
                ));
        // READY - > PROCESSING
        payment.startProcessing();

        // 실제 PG 요청을 보낸 시점
        OffsetDateTime requestedAt = OffsetDateTime.now();


        PaymentGatewayRequest request = new PaymentGatewayRequest(
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency()
        );


        PaymentGatewayResult result = paymentGateway.approve(request);

        switch (result.status()) {

            case SUCCESS -> {
                payment.approve(
                        paymentMethod,
                        result.pgPaymentKey()
                );

                PaymentTransaction transaction =
                        PaymentTransaction.createApproveSuccess(
                                payment,
                                payment.getPaymentProvider(),
                                result.pgPaymentKey(),
                                payment.getAmount(),
                                1,
                                requestedAt
                        );

                paymentTransactionRepository.save(transaction);
            }

            case FAILED -> {
                payment.fail(
                        result.failureCode(),
                        result.failureReason()
                );

                PaymentTransaction transaction =
                        PaymentTransaction.createApproveFailed(
                                payment,
                                payment.getPaymentProvider(),
                                payment.getAmount(),
                                1,
                                result.failureCode(),
                                result.failureReason(),
                                requestedAt
                        );

                paymentTransactionRepository.save(transaction);
            }

            case UNKNOWN -> {
                payment.markUnknown();

                PaymentTransaction transaction =
                        PaymentTransaction.createApproveUnknown(
                                payment,
                                payment.getPaymentProvider(),
                                payment.getAmount(),
                                1,
                                requestedAt
                        );

                paymentTransactionRepository.save(transaction);
            }
        }
    }


    private String createOrderId() {
        return "PAY-" + UUID.randomUUID();
    }

}