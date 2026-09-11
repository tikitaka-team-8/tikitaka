package com.tikitaka.platform.event.infrastructure.client.ticketing.dto;

import java.util.UUID;

public record CreateScheduleSeatsResponse(
    UUID eventSessionId,
    Integer createdCount,
    Integer skippedCount
) {
}
