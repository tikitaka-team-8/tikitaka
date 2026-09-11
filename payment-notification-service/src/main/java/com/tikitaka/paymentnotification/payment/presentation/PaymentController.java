package com.tikitaka.paymentnotification.payment.presentation;


import com.tikitaka.paymentnotification.global.response.ApiResponse;
import com.tikitaka.paymentnotification.payment.application.PaymentService;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.presentation.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PaymentService paymentService;


    //-------------------------------------------------------------------//


    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentById(
            @PathVariable UUID paymentId,
            @RequestHeader(USER_ID_HEADER) @Positive Long loginUserId
    ) {

        PaymentDetailResult result = paymentService.getPaymentById(paymentId, loginUserId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        "결제 단건 조회성공.",
                        PaymentDetailResponse.from(result)
                )
        );
    }


    //-------------------------------------------------------------------//



    @PostMapping("/{paymentId}/approve")
    public ResponseEntity<ApiResponse<PaymentApproveResponse>> approvePayment(
            @PathVariable UUID paymentId,
            @RequestHeader(USER_ID_HEADER) @Positive Long loginUserId,
            @Valid @RequestBody PaymentApproveRequest request
    ) {
        PaymentApproveResult result =
                paymentService.approvePayment(
                        paymentId,
                        loginUserId,
                        request.paymentKey()
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
