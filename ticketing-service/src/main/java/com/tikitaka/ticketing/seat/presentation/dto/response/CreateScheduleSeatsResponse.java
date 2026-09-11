package com.tikitaka.ticketing.seat.presentation.dto.response;

import java.util.UUID;

public record CreateScheduleSeatsResponse(
        UUID eventSessionId,
        int createdCount,
        int skippedCount
) {
    public static CreateScheduleSeatsResponse of(UUID eventSessionId, int createdCount, int skippedCount) {
        return new CreateScheduleSeatsResponse(eventSessionId, createdCount, skippedCount);
    }
}
