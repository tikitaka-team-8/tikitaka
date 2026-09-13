package com.tikitaka.platform.event.presentation.dto.organizer.response;

import com.tikitaka.platform.event.domain.EventStatus;

import java.util.UUID;

public record EventStatusUpdateResponse(
    UUID eventId,
    EventStatus previousStatus,
    EventStatus status,
    int inventorySessionCount,
    int createdSeatCount,
    int skippedSeatCount
) {
}
