package com.tikitaka.ticketing.seat.presentation.dto.request;

import com.tikitaka.ticketing.seat.application.command.CreateScheduleSeatsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record CreateScheduleSeatsRequest(
        @NotNull
        UUID eventSessionId,

        @NotNull
        UUID venueId,

        @NotEmpty
        @Valid
        List<SeatItem> seats
) {
    public record SeatItem(
            @NotNull
            UUID venueSeatId,

            @NotBlank
            String section,

            @NotBlank
            String rowLabel,

            @NotBlank
            String seatNumber,

            @NotBlank
            String grade,

            @NotNull
            @PositiveOrZero
            Long price
    ) {
    }

    public CreateScheduleSeatsCommand toCommand() {
        return new CreateScheduleSeatsCommand(
                eventSessionId,
                venueId,
                seats.stream()
                        .map(seat -> new CreateScheduleSeatsCommand.SeatItem(
                                seat.venueSeatId(),
                                seat.section(),
                                seat.rowLabel(),
                                seat.seatNumber(),
                                seat.grade(),
                                seat.price()
                        ))
                        .toList()
        );
    }
}
