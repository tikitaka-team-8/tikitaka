package com.tikitaka.ticketing.reservation.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "예매 생성 요청")
public class CreateReservationReqDto {

    @NotEmpty
    private List<UUID> seatHoldIds;
}
