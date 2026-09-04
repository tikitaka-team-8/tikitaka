package com.tikitaka.platform.event.application.query;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record PublicEventSearchCondition(
    String keyword,
    UUID venueId
) {

  public static PublicEventSearchCondition of (
    String keyword,
    UUID venueId
  ){
    String normalizedKeyword =
        StringUtils.hasText(keyword) ? keyword.trim() : null;

    return new PublicEventSearchCondition(normalizedKeyword, venueId);
  }
}
