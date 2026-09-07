package com.tikitaka.platform.event.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record EventCreateRequest(
    @NotNull(message = "공연장 ID는 필수입니다.")
    UUID venueId,

    @NotBlank(message = "공연 제목은 필수입니다.")
    @Size(max = 200, message = "공연 제목은 200자 이하여야 합니다.")
    String title,

    String description,

    @NotNull(message = "공연 시간은 필수입니다.")
    @Positive(message = "공연 시간은 1분 이상이어야 합니다.")
    Integer runningTimeMinutes
) {

}
