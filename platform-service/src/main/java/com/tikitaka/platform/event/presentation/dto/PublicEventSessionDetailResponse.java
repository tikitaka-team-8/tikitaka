package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.domain.EventSessionStatus;
import com.tikitaka.platform.event.domain.SessionSectionPrice;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PublicEventSessionDetailResponse(
    UUID eventId,
    UUID sessionId,
    Integer sessionNumber,
    OffsetDateTime performanceStartAt,
    OffsetDateTime performanceEndAt,
    OffsetDateTime salesOpenAt,
    OffsetDateTime salesCloseAt,
    EventSessionStatus status,
    List<SectionPriceResponse> sectionPrices
) {

  public static PublicEventSessionDetailResponse from(
      EventSession session
  ) {
    List<SectionPriceResponse> sectionPrices =
        session.getSectionPrices().stream()
            .filter(SessionSectionPrice::isSalesEnabled)
            .sorted(Comparator.comparing(
                price -> price.getVenueSection().getDisplayOrder()
            ))
            .map(SectionPriceResponse::from)
            .toList();

    return new PublicEventSessionDetailResponse(
        session.getEvent().getId(),
        session.getId(),
        session.getSessionNumber(),
        session.getPerformanceStartAt(),
        session.getPerformanceEndAt(),
        session.getSalesOpenAt(),
        session.getSalesCloseAt(),
        session.getStatus(),
        sectionPrices
    );

  }

}
