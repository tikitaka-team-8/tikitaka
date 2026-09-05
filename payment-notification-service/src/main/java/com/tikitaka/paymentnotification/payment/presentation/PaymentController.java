package com.tikitaka.paymentnotification.payment.presentation;


import com.tikitaka.paymentnotification.global.response.ApiResponse;
import com.tikitaka.paymentnotification.payment.application.PaymentService;
import com.tikitaka.paymentnotification.payment.application.command.PaymentCreateCommand;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentCreateResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {


    private final PaymentService paymentService;


    //-------------------------------------------------------------------//


    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentById(@PathVariable UUID  paymentId){

        PaymentDetailResult result = paymentService.getPaymentById(paymentId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        "결제 단건 조회성공.",
                        PaymentDetailResponse.from(result)
                )
        );
    }


















    //-------------------------------------------------------------------//

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

    @PostMapping("/{paymentId}/approve")
    public ResponseEntity<ApiResponse<PaymentApproveResponse>> approvePayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentApproveRequest request
    ) {
        PaymentApproveResult result =
                paymentService.approvePayment(
                        paymentId,
                        request.paymentMethod()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        "결제 승인 처리가 완료되었습니다.",
                        PaymentApproveResponse.from(result)
                )
        );
    }

}
