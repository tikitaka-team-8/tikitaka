package com.tikitaka.platform.event.application;

import java.util.List;
import java.util.UUID;

public record EventPublicationPlan(
    UUID venueId,
    List<SessionSeats> sessions
) {

  public record SessionSeats(
      UUID eventSessionId,
      List<SeatSnapshot> seats
  ) {
  }

  public record SeatSnapshot(
      UUID venueSeatId,
      String section,
      String rowLabel,
      String seatNumber,
      String grade,
      long price
  ) {
  }
}
