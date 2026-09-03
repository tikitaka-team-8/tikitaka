package com.tikitaka.platform.event.domain;

import java.util.Set;

public enum EventStatus {
  DRAFT,
  UPCOMING,
  ON_SALE,
  SALE_CLOSED,
  COMPLETED,
  CANCELED;

  private static final Set<EventStatus> PUBLIC_STATUSES = Set.of(
      UPCOMING,
      ON_SALE,
      SALE_CLOSED,
      COMPLETED
  );

  public static Set<EventStatus> publicStatuses() {
    return PUBLIC_STATUSES;
  }

  public boolean isPubliclyVisible() {
    return PUBLIC_STATUSES.contains(this);
  }
}
