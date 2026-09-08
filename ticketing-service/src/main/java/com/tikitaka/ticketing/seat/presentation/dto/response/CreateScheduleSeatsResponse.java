package com.tikitaka.ticketing.seat.presentation.dto.response;

public record CreateScheduleSeatsResponse(
        int createdCount,
        int skippedCount
) {
    public static CreateScheduleSeatsResponse of(int createdCount, int skippedCount) {
        return new CreateScheduleSeatsResponse(createdCount, skippedCount);
    }
}
