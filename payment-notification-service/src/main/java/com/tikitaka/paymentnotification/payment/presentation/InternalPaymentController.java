package com.tikitaka.paymentnotification.payment.presentation;

import com.tikitaka.paymentnotification.global.response.ApiResponse;
import com.tikitaka.paymentnotification.payment.application.PaymentService;
import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.presentation.dto.PaymentDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/payments")
public class InternalPaymentController {

    // 아직 보안도입 X라 논리적으로 내부 API 구분만 한 상태입니다.


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



}
