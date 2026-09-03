package com.tikitaka.ticketing.seat.presentation.controller;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.seat.application.service.SeatService;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatListResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.SeatHoldResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class SeatController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SeatService seatService;

    @GetMapping("/{eventSessionId}/seats")
    public ApiResponse<ScheduleSeatListResponse> getSeatList(
            @PathVariable UUID eventSessionId,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String grade,
            @RequestHeader(USER_ID_HEADER) @Positive Long userId,
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
    public ApiResponse<ScheduleSeatResponse> getSeatDetail(
            @PathVariable UUID eventSessionId,
            @PathVariable UUID scheduleSeatId,
            @RequestHeader(USER_ID_HEADER) @Positive Long userId
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

//    @PostMapping("/{eventSessionId}/seats/{scheduleSeatId}/hold")
//    public ApiResponse<SeatHoldResponse> holdSeat(
//            @PathVariable UUID eventSessionId,
//            @PathVariable UUID scheduleSeatId,
//            @RequestHeader(USER_ID_HEADER) @Positive Long userId,
//            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank String idempotencyKey
//    ) {
//        SeatHoldResponse response = seatService.holdSeat(
//                eventSessionId,
//                scheduleSeatId,
//                userId,
//                idempotencyKey
//        );
//        return ApiResponse.success(
//                        HttpStatus.CREATED,
//                        "좌석 선점이 완료되었습니다.",
//                        response
//                );
//    }

}
