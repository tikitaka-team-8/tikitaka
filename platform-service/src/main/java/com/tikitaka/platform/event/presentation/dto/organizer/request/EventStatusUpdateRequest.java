package com.tikitaka.platform.event.presentation.dto.organizer.request;

import jakarta.validation.constraints.NotNull;

public record EventStatusUpdateRequest(

    @NotNull(message = "변경할 공연 상태는 필수입니다.")
    EventStatusChangeTarget targetStatus
) {
}
