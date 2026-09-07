package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.EventSession;

import java.util.UUID;

public record EventSessionCreateResponse(

    UUID sessionId,
    int sessionNumber,
    boolean queueEnabled,
    String status

) {
  public static EventSessionCreateResponse from(EventSession eventSession) {
    return new EventSessionCreateResponse(
        eventSession.getId(),
        eventSession.getSessionNumber(),
        eventSession.isQueueEnabled(),
        eventSession.getStatus().name()
    );
  };
}
