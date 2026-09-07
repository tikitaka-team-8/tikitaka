package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.SessionSectionPrice;

import java.util.List;
import java.util.UUID;

public record SessionSectionPricesResponse(
    UUID sessionId,
    List<SectionPriceResponse> sectionPrices
) {

  public static SessionSectionPricesResponse from(
      UUID sessionId,
      List<SessionSectionPrice> sectionPrices
  ) {
    return new SessionSectionPricesResponse(
        sessionId,
        sectionPrices.stream()
            .map(SectionPriceResponse::from)
            .toList()
    );
  }
}
