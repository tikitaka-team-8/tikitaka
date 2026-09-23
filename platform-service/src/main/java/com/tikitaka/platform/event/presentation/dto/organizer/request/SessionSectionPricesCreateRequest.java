package com.tikitaka.platform.event.presentation.dto.organizer.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

@Schema(description = "공연 회차 구역별 가격 설정 요청")
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

