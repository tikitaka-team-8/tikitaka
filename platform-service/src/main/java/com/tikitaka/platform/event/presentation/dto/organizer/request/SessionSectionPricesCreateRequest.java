package com.tikitaka.platform.event.presentation.dto.organizer.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record SessionSectionPricesCreateRequest(
    @NotEmpty
    List<@NotNull @Valid SectionPriceRequest> sectionPrices
) {

  public record SectionPriceRequest(

      @NotNull
      UUID venueSectionId,

      @NotBlank
      @Size(max = 30)
      String seatGrade,

      @NotNull
      @PositiveOrZero
      Long priceAmount,

      @NotNull
      Boolean salesEnabled
  ) {
  }
}

