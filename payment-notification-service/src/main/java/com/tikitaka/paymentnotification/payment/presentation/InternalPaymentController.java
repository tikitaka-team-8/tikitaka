package com.tikitaka.paymentnotification.payment.presentation;

import com.tikitaka.paymentnotification.global.response.ApiResponse;
import com.tikitaka.paymentnotification.payment.application.PaymentService;
import com.tikitaka.paymentnotification.payment.application.command.PaymentCreateCommand;
import com.tikitaka.paymentnotification.payment.application.result.PaymentCreateResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.presentation.dto.PaymentCreateRequest;
import com.tikitaka.paymentnotification.payment.presentation.dto.PaymentCreateResponse;
import com.tikitaka.paymentnotification.payment.presentation.dto.PaymentDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/payments")
public class InternalPaymentController {

    // TODO : 아직 보안도입 X라 논리적으로 내부 API 구분만 한 상태입니다.


    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentByReservationId(@RequestParam UUID reservationId)
    {
        PaymentDetailResult result = paymentService.getPaymentByReservationId(reservationId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        "예매 단건 조회성공.",
                        PaymentDetailResponse.from(result)
                )
        );
    }


    @PostMapping
    public ResponseEntity<ApiResponse<PaymentCreateResponse>> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        PaymentCreateCommand command = new PaymentCreateCommand(
                request.reservationId(),
                request.userId(),
                idempotencyKey,
                request.totalAmount()
        );

        PaymentCreateResult result = paymentService.createPayment(command);

        PaymentCreateResponse response = PaymentCreateResponse.from(result);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "결제가 생성되었습니다.",
                        response
                ));
    }



}
