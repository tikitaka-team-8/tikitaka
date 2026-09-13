package com.tikitaka.ticketing.seat.presentation.controller;


import com.tikitaka.ticketing.seat.application.command.CreateScheduleSeatsCommand;
import com.tikitaka.ticketing.seat.application.service.SeatService;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
import com.tikitaka.ticketing.seat.presentation.dto.request.CreateScheduleSeatsRequest;
import com.tikitaka.ticketing.seat.presentation.dto.request.SeatReleaseRequest;
import com.tikitaka.ticketing.seat.presentation.dto.response.CreateScheduleSeatsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/internal/")
@RequiredArgsConstructor
public class InternalSeatController {

    private final SeatService seatService;

    @PostMapping("/seat-holds/{seatHoldId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseHold(
            @PathVariable UUID seatHoldId,
            @Valid @RequestBody SeatReleaseRequest request
    ) {
        ReleaseReason reason = ReleaseReason.valueOf(request.reason());

        seatService.releaseHold(seatHoldId, reason);

    }
    @PostMapping("/event-sessions/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CreateScheduleSeatsResponse> createScheduleSeats(
            @Valid @RequestBody List<@Valid CreateScheduleSeatsRequest> requests
    ) {
        List<CreateScheduleSeatsCommand> commands = requests.stream()
                .map(CreateScheduleSeatsRequest::toCommand)
                .toList();
        return seatService.createScheduleSeatsBatch(commands);
    }

}
