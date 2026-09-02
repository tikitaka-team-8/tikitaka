package com.tikitaka.ticketing.seat.presentation.dto.response;


import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;

import java.util.List;

public record ScheduleSeatListResponse(
        List<ScheduleSeatResponse> seats
) {
    public static ScheduleSeatListResponse from(
            List<ScheduleSeat> scheduleSeats
    ) {
        return new ScheduleSeatListResponse(
                scheduleSeats.stream()
                        .map(ScheduleSeatResponse::from)
                        .toList()
        );
    }
}
