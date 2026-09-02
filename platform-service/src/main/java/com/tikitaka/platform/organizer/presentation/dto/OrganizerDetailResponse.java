package com.tikitaka.platform.organizer.presentation.dto;

import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.domain.OrganizerStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizerDetailResponse(
    UUID organizerId,
    String name,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String description,
    OrganizerStatus status,
    OffsetDateTime approvedAt
) {

  public static OrganizerDetailResponse from(Organizer organizer) {
    return new OrganizerDetailResponse(
        organizer.getId(),
        organizer.getName(),
        organizer.getRepresentativeName(),
        organizer.getContactEmail(),
        organizer.getContactPhone(),
        organizer.getDescription(),
        organizer.getStatus(),
        organizer.getApprovedAt()
    );
  }
}
