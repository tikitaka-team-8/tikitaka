package com.tikitaka.ticketing.reservation.presentation.controller;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.reservation.application.ReservationService;
import com.tikitaka.ticketing.reservation.application.command.PaymentValidationCommand;
import com.tikitaka.ticketing.reservation.application.result.PaymentValidationResult;
import com.tikitaka.ticketing.reservation.presentation.dto.request.PaymentValidationReqDto;
import com.tikitaka.ticketing.reservation.presentation.dto.response.PaymentValidationResDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/reservations")
public class InternalReservationController {

    private final ReservationService reservationService;

    public InternalReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{reservationId}/payment-validation")
    public ResponseEntity<ApiResponse<PaymentValidationResDto>> validatePayment(
            @PathVariable UUID reservationId,
            @Valid @RequestBody PaymentValidationReqDto requestDto) {

        PaymentValidationCommand command = new PaymentValidationCommand(reservationId, requestDto.getUserId());
        PaymentValidationResult result = reservationService.validatePayment(command);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "결제 전 예매 검증에 성공했습니다.", new PaymentValidationResDto(result))
        );
    }
}
