package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.command.PaymentCreateCommand;
import com.tikitaka.paymentnotification.payment.application.gateway.*;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentCreateResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentFailedEvent;
import com.tikitaka.paymentnotification.payment.domain.event.PaymentSucceededEvent;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxRepository;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransaction;
import com.tikitaka.paymentnotification.payment.domain.transaction.PaymentTransactionRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final ReservationPaymentValidator  reservationPaymentValidator;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentTransactionRepository paymentTransactionRepository;

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentEventSerializer  paymentEventSerializer;


    // 결제 정보 단건 조회
    public PaymentDetailResult getPaymentById(UUID payment_id){
        Payment payment = paymentRepository.findById(payment_id).orElseThrow(()->
                new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        return PaymentDetailResult.from(payment);
    }

    // 예매별 결제 조회
    public PaymentDetailResult getPaymentByReservationId(UUID reservationId){
        Payment payment = paymentRepository.findByReservationId(reservationId).orElseThrow(()->
                new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        return PaymentDetailResult.from(payment);
    }




    // 결제 생성
    @Transactional
    public PaymentCreateResult createPayment(PaymentCreateCommand command) {

        Optional<Payment> paymentByIdempotencyKey = paymentRepository.findByIdempotencyKey(command.idempotencyKey());

        // 멱등키가 존재한다면
        if (paymentByIdempotencyKey.isPresent()){
            Payment existingPayment = paymentByIdempotencyKey.get();
            // 받은 멱등키로 조회한 payment와 비교
            if(!existingPayment.isSameRequest(
                    command.reservationId(),
                    command.userId(),
                    command.totalAmount()
            )){
                // 멱등키 불일치로 인한 중복 결제 요청
                throw new PaymentException(PaymentErrorCode.DUPLICATE_PAYMENT_REQUEST);
            }
            return PaymentCreateResult.from(existingPayment);
        }

        // 예약 중복 결제 요청
        paymentRepository.findByReservationId(command.reservationId()).ifPresent(
                payment -> {throw new PaymentException(PaymentErrorCode.DUPLICATE_PAYMENT_REQUEST);}
        );

        String orderId = "PAY-" + UUID.randomUUID();

        Payment payment = Payment.create(
                command.reservationId(),
                command.userId(),
                orderId,
                command.idempotencyKey(),
                command.totalAmount(),
                PaymentProvider.MOCK //MVP MOCK 처리
        );

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentCreateResult.from(savedPayment);
    }



    // 결제 승인
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

        validateReservation(payment);

        // 실제 PG 요청을 보낸 시점
        OffsetDateTime requestedAt = OffsetDateTime.now();


        PaymentGatewayRequest request = new PaymentGatewayRequest(
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency()
        );


        PaymentGatewayResult result = paymentGateway.approve(request);

        switch (result.status()) {
            case SUCCESS ->
                    handleApproveSuccess(
                            payment,
                            paymentMethod,
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

    // 실제 결제 전 검증 요청
    private void validateReservation(Payment payment){
        ReservationPaymentValidationResult validationResult =
                reservationPaymentValidator.validate(
                        payment.getReservationId(),
                        payment.getUserId()
                );
        if(!Objects.equals(payment.getAmount(),validationResult.totalAmount())){
            throw new PaymentException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }


    // 승인 + 성공 Outbox
    private void handleApproveSuccess(
            Payment payment,
            PaymentMethod paymentMethod,
            PaymentGatewayResult result,
            OffsetDateTime requestedAt
    ) {
        payment.approve(
                paymentMethod,
                result.pgPaymentKey()
        );

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








    private String createOrderId() {
        return "PAY-" + UUID.randomUUID();
    }

}