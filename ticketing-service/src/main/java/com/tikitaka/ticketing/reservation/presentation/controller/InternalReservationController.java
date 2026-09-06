package com.tikitaka.ticketing.reservation.presentation.controller;

import com.tikitaka.ticketing.reservation.application.ReservationService;
import com.tikitaka.ticketing.reservation.application.command.PaymentValidationCommand;
import com.tikitaka.ticketing.reservation.application.result.PaymentValidationResult;
import com.tikitaka.ticketing.reservation.presentation.dto.request.PaymentValidationReqDto;
import com.tikitaka.ticketing.reservation.presentation.dto.response.PaymentValidationResDto;
import com.tikitaka.ticketing.reservation.presentation.security.ReservationInternalServiceKeyValidator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/reservations")
public class InternalReservationController {

    private final ReservationService reservationService;
    private final ReservationInternalServiceKeyValidator internalServiceKeyValidator;

    public InternalReservationController(ReservationService reservationService,
            ReservationInternalServiceKeyValidator internalServiceKeyValidator) {
        this.reservationService = reservationService;
        this.internalServiceKeyValidator = internalServiceKeyValidator;
    }

    @PostMapping("/{reservationId}/payment-validation")
    public ResponseEntity<PaymentValidationResDto> validatePayment(
            @PathVariable UUID reservationId,
            @RequestHeader(value = "X-Service-Key", required = false) String serviceKey, // 헤더 누락, 키 불일치를 Validator에서 동일한 인증 실패(401)로 처리
            @Valid @RequestBody PaymentValidationReqDto requestDto) {

        internalServiceKeyValidator.validate(serviceKey);

        PaymentValidationCommand command = new PaymentValidationCommand(reservationId, requestDto.getUserId());
        PaymentValidationResult result = reservationService.validatePayment(command);

        return ResponseEntity.ok(new PaymentValidationResDto(result));
    }
}
