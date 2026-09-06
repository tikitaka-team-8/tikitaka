package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.domain.EventSessionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicEventSessionResponse(
    UUID sessionId,
    Integer sessionNumber,
    OffsetDateTime performanceStartAt,
    OffsetDateTime salesOpenAt,
    OffsetDateTime salesCloseAt,
    EventSessionStatus status
) {

  public static PublicEventSessionResponse from(EventSession session) {
    return new PublicEventSessionResponse(
        session.getId(),
        session.getSessionNumber(),
        session.getPerformanceStartAt(),
        session.getSalesOpenAt(),
        session.getSalesCloseAt(),
        session.getStatus()
    );
  }
}
