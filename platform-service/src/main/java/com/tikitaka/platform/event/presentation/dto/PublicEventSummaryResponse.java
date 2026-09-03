package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.application.query.PublicEventSummaryResult;

import java.util.UUID;

public record PublicEventSummaryResponse(
    UUID eventId,
    String title,
    String venueName,
    String status
) {

  public static PublicEventSummaryResponse from(PublicEventSummaryResult result) {
    return new PublicEventSummaryResponse(
        result.eventId(),
        result.title(),
        result.venueName(),
        result.status().name()
    );
  }
}
