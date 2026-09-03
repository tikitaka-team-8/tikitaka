package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.venue.domain.Venue;

import java.util.UUID;

public record PublicEventVenueResponse(
    UUID venueId,
    String name,
    String address
) {
  public static PublicEventVenueResponse from(Venue venue) {
    return new PublicEventVenueResponse(
        venue.getId(),
        venue.getName(),
        venue.getAddress()
    );
  }
}
