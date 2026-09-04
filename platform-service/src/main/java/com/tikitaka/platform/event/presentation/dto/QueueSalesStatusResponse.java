package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.domain.EventSessionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QueueSalesStatusResponse(
    UUID sessionId,
    EventSessionStatus sessionStatus,
    OffsetDateTime salesOpenAt,
    OffsetDateTime salesCloseAt,
    boolean queueEnabled
) {

  public static QueueSalesStatusResponse from(EventSession session) {
    return new QueueSalesStatusResponse(
        session.getId(),
        session.getStatus(),
        session.getSalesOpenAt(),
        session.getSalesCloseAt(),
        session.isQueueEnabled()
    );
  }
}
