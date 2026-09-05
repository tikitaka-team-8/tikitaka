package com.tikitaka.platform.event.domain;

import java.util.Set;

public enum EventSessionStatus {
  SCHEDULED,
  COMPLETED,
  CANCELED;

  private static final Set<EventSessionStatus> RESERVABLE_STATUSES = Set.of(
      SCHEDULED
  );

  public boolean isReservable() {
    return RESERVABLE_STATUSES.contains(this);
  }
}


