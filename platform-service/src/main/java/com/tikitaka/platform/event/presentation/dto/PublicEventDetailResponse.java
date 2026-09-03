package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSessionStatus;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PublicEventDetailResponse(
    UUID eventId,
    String title,
    String description,
    Integer runningTimeMinutes,
    String status,
    PublicEventVenueResponse venue,
    List<PublicEventSessionResponse> sessions
) {

  public static PublicEventDetailResponse from(Event event) {

    List<PublicEventSessionResponse> list = event.getSessions()
        .stream()
        .filter(session ->
            session.getStatus() != EventSessionStatus.CANCELED
        )
        .sorted(Comparator.comparing(
            session -> session.getSessionNumber()
        ))
        .map(PublicEventSessionResponse::from)
        .toList();

    return new PublicEventDetailResponse(
        event.getId(),
        event.getTitle(),
        event.getDescription(),
        event.getRunningTimeMinutes(),
        event.getStatus().name(),
        PublicEventVenueResponse.from(event.getVenue()),
        list
    );

  }
}
