package com.tikitaka.ticketing.seat.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SeatReleaseRequest(
        @NotBlank
        String reason
) {
}
