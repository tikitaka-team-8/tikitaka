package com.tikitaka.ticketing.seat.presentation.controller;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.seat.application.service.SeatService;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatListResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class SeatController {

    // TODO 사용자 권한 검증
    // - X-User-Id / X-User-Role 검증
    // - USER / ADMIN 권한에 따른 조회 범위 제한

    // TODO 대기열 검증
    // - 대기열 검증 메서드 구현 완료 시 연동
    // - 좌석 조회 시 대기열 검증 후 선점 진행

    private final SeatService seatService;

    @GetMapping("/{eventSessionId}/seats")
    public ApiResponse<ScheduleSeatListResponse> getSeatList(
            @PathVariable UUID eventSessionId,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String grade){
        ScheduleSeatListResponse response = seatService.getSeatList(eventSessionId,section,grade);
        return ApiResponse.success(
                HttpStatus.OK,
                "조회가 완료되었습니다.",
                response
        );
    }

    @GetMapping("/{eventSessionId}/seats/{scheduleSeatId}")
    public ApiResponse<ScheduleSeatResponse> getSeatDetail(
            @PathVariable UUID eventSessionId,
            @PathVariable UUID scheduleSeatId
            ){
        ScheduleSeatResponse response = seatService.getSeatDetail(eventSessionId,scheduleSeatId);
        return ApiResponse.success(
                HttpStatus.OK,
                "조회가 완료되었습니다.",
                response
        );
    }

}
