package com.tikitaka.platform.event.infrastructure.client.ticketing.dto;

import com.tikitaka.platform.event.application.EventPublicationPlan;

import java.util.List;
import java.util.UUID;

public record CreateScheduleSeatsRequest(
    UUID venueId,
    List<SeatRequest> seats
) {

  public static CreateScheduleSeatsRequest from(
      UUID venueId,
      EventPublicationPlan.SessionSeats session
  ) {

    List<SeatRequest> seats = session.seats().stream()
        .map(seat -> new SeatRequest(
            seat.venueSeatId(),
            seat.section(),
            seat.rowLabel(),
            seat.seatNumber(),
            seat.grade(),
            seat.price()
        ))
        .toList();

    return new CreateScheduleSeatsRequest(
        venueId,
        seats
    );
  }

  public record SeatRequest(
      UUID venueSeatId,
      String section,
      String rowLabel,
      String seatNumber,
      String grade,
      long price
  ) {
  }
}
