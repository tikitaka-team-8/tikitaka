package com.tikitaka.paymentnotification.payment.presentation;

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


    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<PaymentDetailResponse> getPaymentByReservationId(@RequestParam UUID reservationId) {
        PaymentDetailResult result =
                paymentService.getPaymentByReservationId(reservationId);

        return ResponseEntity.ok(PaymentDetailResponse.from(result));
    }




    @PostMapping
    public ResponseEntity<PaymentCreateResponse> createPayment(
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

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PaymentCreateResponse.from(result));
    }



}
