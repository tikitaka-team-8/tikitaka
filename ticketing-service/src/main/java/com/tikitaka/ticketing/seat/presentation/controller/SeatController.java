package com.tikitaka.ticketing.seat.presentation.controller;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.global.response.PageMeta;
import com.tikitaka.ticketing.seat.application.service.SeatService;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.SeatHoldResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ApiResponse<List<ScheduleSeatResponse>> getSeatList(
            @PathVariable UUID eventSessionId,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String grade,
            @RequestHeader(USER_ID_HEADER) @Positive Long userId,
            @RequestHeader(QUEUE_TOKEN_HEADER) @NotBlank String queueToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size){
        Page<ScheduleSeatResponse> seatPage = seatService.getSeatList(
                eventSessionId,
                section,
                grade,
                userId,
                queueToken,
                page,
                size
        );

        PageMeta meta = new PageMeta(
                seatPage.getNumber(),
                seatPage.getSize(),
                seatPage.getTotalElements(),
                seatPage.getTotalPages(),
                seatPage.hasNext()
        );

        return ApiResponse.success(
                HttpStatus.OK,
                "조회가 완료되었습니다.",
                seatPage.getContent(),
                meta
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

    @PostMapping("/{eventSessionId}/seats/{scheduleSeatId}/hold")
    public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeat(
            @PathVariable UUID eventSessionId,
            @PathVariable UUID scheduleSeatId,
            @RequestHeader(USER_ID_HEADER) @Positive Long userId,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank String idempotencyKey
    ) {
        SeatHoldResponse response = seatService.holdSeat(
                eventSessionId,
                scheduleSeatId,
                userId,
                idempotencyKey
        );
        // ApiResponse.success(HttpStatus.CREATED, ...)는 응답 바디의 status 필드만 201로 채울 뿐,
        // ResponseEntity로 감싸지 않으면 실제 HTTP 응답 코드는 Spring 기본값인 200으로 나간다
        // (S02 동시성 테스트에서 실측으로 확인된 버그 - k6가 실제 201을 기준으로 성공을 판정하는데
        // 서버는 200을 반환해서 승자가 "예상 못한 실패"로 잘못 집계됐었다). ReservationController처럼
        // ResponseEntity.status(...)로 감싸서 바디의 status 필드와 실제 HTTP 응답 코드를 일치시킨다.
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        HttpStatus.CREATED,
                        "좌석 선점이 완료되었습니다.",
                        response
                )
        );
    }

    @DeleteMapping("/seat-holds/{seatHoldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelHold(
            @PathVariable UUID seatHoldId,
            @RequestHeader(USER_ID_HEADER) @Positive Long userId
    ) {
        seatService.cancelHold(seatHoldId, userId);

    }


}
