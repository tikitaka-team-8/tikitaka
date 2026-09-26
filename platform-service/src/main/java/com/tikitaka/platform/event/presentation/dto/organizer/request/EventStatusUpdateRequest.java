package com.tikitaka.platform.event.presentation.dto.organizer.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "공연 상태 변경 요청")
public record EventStatusUpdateRequest(

    @NotNull(message = "변경할 공연 상태는 필수입니다.")
    EventStatusChangeTarget targetStatus
) {
}
