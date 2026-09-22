package com.tikitaka.ticketing.seat.domain.projection;

import com.tikitaka.ticketing.seat.domain.enums.SeatStatus;

import java.util.UUID;

//좌석 목록 조회(GET .../seats) 응답에 실제로 필요한 필드만 담는 프로젝션.-7개 필요한 필드만 SELECT

public record ScheduleSeatSummary(
        UUID scheduleSeatId,
        String section,
        String rowLabel,
        String seatNumber,
        String seatGrade,
        Long price,
        SeatStatus seatStatus
) {
}
