package com.tikitaka.platform.event.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

public record PublicEventListRequest(

    @Size(max = 200)
    String keyword,

    UUID venueId,

    @Min(0)
    Integer page,

    @Min(1)
    @Max(100)
    Integer size

) {

  public PageRequest toPageRequest() {
    int resolvedPage = page == null ? 0 : page;
    int resolvedSize = size == null ? 20 : size;

    return PageRequest.of(resolvedPage, resolvedSize);
  }
}