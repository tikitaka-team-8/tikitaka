package com.tikitaka.ticketing.reservation.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateReservationReqDto {

    @NotEmpty
    private List<UUID> seatHoldIds;
}
