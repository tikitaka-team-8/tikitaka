package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.command.PaymentCreateCommand;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventSerializer;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayRequest;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentCreateResult;
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

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentEventSerializer  paymentEventSerializer;


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
    public PaymentApproveResult approvePayment(
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

                // Payment 승인 상태 변경
                payment.approve(paymentMethod, result.pgPaymentKey());

                // 결제 이력 저장
                paymentTransactionRepository.save(
                        PaymentTransaction.createApproveSuccess(
                                payment,
                                payment.getPaymentProvider(),
                                result.pgPaymentKey(),
                                payment.getAmount(),
                                1,
                                requestedAt
                        )
                );

                // 결제 성공 이벤트 저장
                PaymentSucceededEvent event =
                        PaymentSucceededEvent.from(payment);

                // 이벤트 -> JSON
                String payload =
                        paymentEventSerializer.serialize(event);

                // Outbox 생성
                PaymentOutbox outbox =
                        PaymentOutbox.create(
                                payment,
                                event.eventType(),
                                payload
                        );
                // Outbox 저장
                paymentOutboxRepository.save(outbox);
            }

            case FAILED -> {
                payment.fail(result.failureCode(), result.failureReason());

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

                String payload =
                        paymentEventSerializer.serialize(event);

                PaymentOutbox outbox =
                        PaymentOutbox.create(
                                payment,
                                event.eventType(),
                                payload
                        );

                paymentOutboxRepository.save(outbox);
            }


            case UNKNOWN -> {
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
        }
        return PaymentApproveResult.from(payment);
    }


    private String createOrderId() {
        return "PAY-" + UUID.randomUUID();
    }

}