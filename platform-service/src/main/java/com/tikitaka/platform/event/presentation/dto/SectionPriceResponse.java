package com.tikitaka.platform.event.presentation.dto;

import com.tikitaka.platform.event.domain.SessionSectionPrice;

import java.util.UUID;

public record SectionPriceResponse(

    UUID venueSectionId,
    String sectionName,
    String seatGrade,
    long priceAmount
) {
  public static SectionPriceResponse from(
      SessionSectionPrice sectionPrice
  ) {

    return new SectionPriceResponse(
        sectionPrice.getVenueSection().getId(),
        sectionPrice.getVenueSection().getName(),
        sectionPrice.getSeatGrade(),
        sectionPrice.getPriceAmount()
    );

  }
}
