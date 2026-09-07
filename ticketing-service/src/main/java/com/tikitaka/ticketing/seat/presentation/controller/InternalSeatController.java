package com.tikitaka.ticketing.seat.presentation.controller;


import com.tikitaka.ticketing.seat.application.service.SeatService;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
import com.tikitaka.ticketing.seat.presentation.dto.request.CreateScheduleSeatsRequest;
import com.tikitaka.ticketing.seat.presentation.dto.request.SeatReleaseRequest;
import com.tikitaka.ticketing.seat.presentation.dto.response.CreateScheduleSeatsResponse;
import com.tikitaka.ticketing.seat.presentation.security.InternalAuthValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/")
@RequiredArgsConstructor
public class InternalSeatController {

    private final  SeatService seatService;
    private final InternalAuthValidator internalAuthValidator;

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    @PostMapping("/seat-holds/{seatHoldId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public  void releaseHold(
            @PathVariable UUID seatHoldId,
            @RequestHeader(INTERNAL_AUTH_HEADER) @NotBlank String internalAuthToken,
            @Valid @RequestBody SeatReleaseRequest request
    ) {
        internalAuthValidator.validate(internalAuthToken);
        ReleaseReason reason = ReleaseReason.valueOf(request.reason());

        seatService.releaseHold(seatHoldId, reason);

    }

    // TODO: 내부 서비스 인증(X-Internal-Auth) 필터 적용 예정 - 필터 도입 전까지 인증 검증 없이 동작함
    @PostMapping("/event-sessions/{eventSessionId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateScheduleSeatsResponse createScheduleSeats(
            @PathVariable UUID eventSessionId,
            @Valid @RequestBody CreateScheduleSeatsRequest request
    ) {
        return seatService.createScheduleSeats(request.toCommand(eventSessionId));
    }
}
