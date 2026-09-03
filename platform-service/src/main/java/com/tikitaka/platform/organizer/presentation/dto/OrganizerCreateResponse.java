package com.tikitaka.platform.organizer.presentation.dto;

import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.domain.OrganizerStatus;

import java.util.UUID;

public record OrganizerCreateResponse(
    UUID organizerId,
    OrganizerStatus status
) {

  public static OrganizerCreateResponse from(Organizer organizer) {
    return new OrganizerCreateResponse(
        organizer.getId(),
        organizer.getStatus()
    );
  }
}
