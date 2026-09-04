package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.EventSession;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventSessionInfoResponse(
    UUID eventSessionId,
    UUID eventId,
    String eventTitle,
    OffsetDateTime sessionStartAt
) {
  public static EventSessionInfoResponse from(EventSession eventSession) {
    return new EventSessionInfoResponse(
        eventSession.getId(),
        eventSession.getEvent().getId(),
        eventSession.getEvent().getTitle(),
        eventSession.getPerformanceStartAt()
    );
  }
}
