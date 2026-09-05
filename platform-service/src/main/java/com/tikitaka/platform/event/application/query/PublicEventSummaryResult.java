package com.tikitaka.platform.event.application.query;

import com.tikitaka.platform.event.domain.EventStatus;

import java.util.UUID;

public record PublicEventSummaryResult(
    UUID eventId,
    String title,
    String venueName,
    EventStatus status
) {
}
