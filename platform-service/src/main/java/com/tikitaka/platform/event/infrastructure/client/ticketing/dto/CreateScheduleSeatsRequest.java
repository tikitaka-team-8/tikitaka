package com.tikitaka.platform.event.infrastructure.client.ticketing.dto;

import com.tikitaka.platform.event.application.EventPublicationPlan;

import java.util.List;
import java.util.UUID;

public record CreateScheduleSeatsRequest(
    UUID eventSessionId,
    UUID venueId,
    List<SeatItem> seats
) {

  public static CreateScheduleSeatsRequest from(
      UUID venueId,
      EventPublicationPlan.SessionSeats session
  ) {

    List<SeatItem> seats = session.seats().stream()
        .map(seat -> new SeatItem(
            seat.venueSeatId(),
            seat.section(),
            seat.rowLabel(),
            seat.seatNumber(),
            seat.grade(),
            seat.price()
        ))
        .toList();

    return new CreateScheduleSeatsRequest(
        session.eventSessionId(),
        venueId,
        seats
    );
  }

  public record SeatItem(
      UUID venueSeatId,
      String section,
      String rowLabel,
      String seatNumber,
      String grade,
      long price
  ) {
  }
}
