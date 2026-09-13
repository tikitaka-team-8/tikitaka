package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.command.PaymentCreateCommand;
import com.tikitaka.paymentnotification.payment.application.gateway.*;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentCreateResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ReservationPaymentValidator reservationPaymentValidator;

    private final PaymentKeyBinder paymentKeyBinder;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    private final PaymentProcessingCompensator paymentProcessingCompensator;
    private final PaymentProcessingAcquirer paymentProcessingAcquirer;
    private final PaymentApprovalResultProcessor paymentApprovalResultProcessor;

    private final PaymentQueryGateway paymentQueryGateway;

    // 결제 정보 단건 조회
    @Transactional(readOnly = true)
    public PaymentDetailResult getPaymentById(UUID paymentId, Long loginUserId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() ->
                new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        validateOwner(payment, loginUserId);

        return PaymentDetailResult.from(payment);
    }

    // 예매별 결제 조회
    @Transactional(readOnly = true)
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
                PaymentProvider.TOSS //MVP MOCK 처리
        );

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentCreateResult.from(savedPayment);
    }



    // 결제 승인
    public PaymentApproveResult approvePayment(
            UUID paymentId,
            Long loginUserId,
            String paymentKey
    ) {
        // 승인에 사용할 Payment 정보 조회
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(()->
                new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        validateOwner(payment, loginUserId);

        // READY -> PROCESSING 선점
        boolean acquired = paymentProcessingAcquirer.acquire(paymentId);

        if(!acquired){
            return handleAcquireFailure(paymentId);
        }

        // PG 호출 전 단계
        try{
            validateReservation(payment);

            // Toss confirm 전에 paymentKey 저장
            paymentKeyBinder.bind(paymentId, paymentKey);

        }catch (RuntimeException e){
            // PG 호출 전 실패이므로 PROCESSING -> READY 복구
            paymentProcessingCompensator.restoreReady(paymentId);
            throw e;
        }


        OffsetDateTime requestedAt = OffsetDateTime.now();

        PaymentGatewayRequest request = new PaymentGatewayRequest(
                paymentKey,
                payment.getOrderId(),
                payment.getAmount()
        );

        PaymentGatewayResult result;

        // PG 승인 요청
        try {
            result = paymentGateway.approve(request);
        } catch (RuntimeException e) {
            // PG 요청 이후 결과를 확신할 수 없으므로 UNKNOWN
            markUnknownSafely(paymentId, requestedAt);
            throw e;
        }

        // PG 결과를 DB에 최종 반영
        try {
            return paymentApprovalResultProcessor.process(
                    paymentId,
                    result,
                    requestedAt
            );
        } catch (RuntimeException e) {
            // PG 결과를 받은 이후 DB 반영에 실패했으므로
            // 실제 결제 결과와 DB 상태가 다를 가능성이 있어 UNKNOWN
            markUnknownSafely(paymentId, requestedAt);
            throw e;
        }
    }

    // ------------------------------------------------------------------ //


    private void validateOwner(Payment payment, Long loginUserId) {
        if (!Objects.equals(payment.getUserId(), loginUserId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
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

    // 선점 실패 처리
    private PaymentApproveResult handleAcquireFailure(UUID paymentId){
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(()->
                        new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        return switch (payment.getStatus()){
            case APPROVED ->
                    PaymentApproveResult.from(payment);

            case UNKNOWN ->
                    recoverUnknownPayment(payment);

            case READY, PROCESSING, FAILED, CANCELED ->
                    throw new PaymentException(
                            PaymentErrorCode.PAYMENT_NOT_ALLOWED
                    );
        };
    }

    private void markUnknownSafely(
            UUID paymentId,
            OffsetDateTime requestedAt
    ) {
        try {
            paymentApprovalResultProcessor.markUnknown(
                    paymentId,
                    requestedAt
            );
        } catch (RuntimeException e) {
            log.error(
                    "결제를 UNKNOWN 상태로 전환하지 못했습니다. paymentId={}",
                    paymentId,
                    e
            );
        }
    }


    private PaymentApproveResult recoverUnknownPayment(Payment payment) {
        String paymentKey = payment.getPgPaymentKey();

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_ALLOWED);
        }

        PaymentQueryResult queryResult = paymentQueryGateway.getPayment(paymentKey);

        validateQueriedPayment(payment, queryResult);

        if (!"DONE".equals(queryResult.status())) { return PaymentApproveResult.from(payment); }

        return paymentApprovalResultProcessor.recoverApproved(
                payment.getPaymentId(),
                queryResult
        );
    }


    private void validateQueriedPayment(Payment payment, PaymentQueryResult queryResult) {
        if (!Objects.equals(
                payment.getPgPaymentKey(),
                queryResult.paymentKey()
        )) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_ALLOWED);
        }

        if (!Objects.equals(
                payment.getOrderId(),
                queryResult.orderId()
        )) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_ALLOWED);
        }
    }


}
