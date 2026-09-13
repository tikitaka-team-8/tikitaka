package com.tikitaka.platform.event.presentation.dto.organizer.response;

import com.tikitaka.platform.event.domain.Event;

import java.util.UUID;

public record EventCreateResponse(
    UUID eventId,
    String status
) {

  public static EventCreateResponse from(Event event) {
    return new EventCreateResponse(
        event.getId(),
        event.getStatus().name()
    );
  }
}
