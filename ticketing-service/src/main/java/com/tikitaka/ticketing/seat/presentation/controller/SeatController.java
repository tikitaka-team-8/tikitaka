package com.tikitaka.ticketing.seat.presentation.controller;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.seat.application.service.SeatService;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatListResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.SeatHoldResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Seat", description = "회차 좌석과 좌석 선점 API")
@SecurityRequirement(name = "bearerAuth")
public class SeatController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SeatService seatService;

    @GetMapping("/{eventSessionId}/seats")
    @Operation(summary = "회차 좌석 목록 조회")
    public ApiResponse<ScheduleSeatListResponse> getSeatList(
            @PathVariable UUID eventSessionId,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String grade,
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) @Positive Long userId,
            @RequestHeader(QUEUE_TOKEN_HEADER) @NotBlank String queueToken){
        ScheduleSeatListResponse response = seatService.getSeatList(
                eventSessionId,
                section,
                grade,
                userId,
                queueToken
        );
        return ApiResponse.success(
                HttpStatus.OK,
                "조회가 완료되었습니다.",
                response
        );
    }

    @GetMapping("/{eventSessionId}/seats/{scheduleSeatId}")
    @Operation(summary = "회차 좌석 상세 조회")
    public ApiResponse<ScheduleSeatResponse> getSeatDetail(
            @PathVariable UUID eventSessionId,
            @PathVariable UUID scheduleSeatId,
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) @Positive Long userId
            ){
        ScheduleSeatResponse response = seatService.getSeatDetail(
                eventSessionId,
                scheduleSeatId,
                userId
        );
        return ApiResponse.success(
                HttpStatus.OK,
                "조회가 완료되었습니다.",
                response
        );
    }

    @PostMapping("/{eventSessionId}/seats/{scheduleSeatId}/hold")
    @Operation(summary = "좌석 선점")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "좌석 선점 성공"))
    public ApiResponse<SeatHoldResponse> holdSeat(
            @PathVariable UUID eventSessionId,
            @PathVariable UUID scheduleSeatId,
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) @Positive Long userId,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank String idempotencyKey
    ) {
        SeatHoldResponse response = seatService.holdSeat(
                eventSessionId,
                scheduleSeatId,
                userId,
                idempotencyKey
        );
        return ApiResponse.success(
                        HttpStatus.CREATED,
                        "좌석 선점이 완료되었습니다.",
                        response
                );
    }

    @DeleteMapping("/seat-holds/{seatHoldId}")
    @Operation(summary = "좌석 선점 취소")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "좌석 선점 취소 성공"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelHold(
            @PathVariable UUID seatHoldId,
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) @Positive Long userId
    ) {
        seatService.cancelHold(seatHoldId, userId);

    }


}
