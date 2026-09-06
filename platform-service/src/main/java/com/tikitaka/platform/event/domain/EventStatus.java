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

  private static final Set<EventStatus> QUEUE_SALES_STATUSES = Set.of(
      UPCOMING,
      ON_SALE
  );

  private static final Set<EventStatus> RESERVABLE_STATUSES = Set.of(
      ON_SALE
  );

  public static Set<EventStatus> publicStatuses() {
    return PUBLIC_STATUSES;
  }

  public boolean isPubliclyVisible() {
    return PUBLIC_STATUSES.contains(this);
  }

  public boolean allowsQueueSale() {
    return QUEUE_SALES_STATUSES.contains(this);
  }

  public boolean isReservable() {
    return RESERVABLE_STATUSES.contains(this);
  }
}
