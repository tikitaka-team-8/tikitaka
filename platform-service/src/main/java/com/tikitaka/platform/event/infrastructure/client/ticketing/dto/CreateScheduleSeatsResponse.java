package com.tikitaka.platform.event.infrastructure.client.ticketing.dto;

public record CreateScheduleSeatsResponse(
    int createdCount,
    int skippedCount
) {
}
