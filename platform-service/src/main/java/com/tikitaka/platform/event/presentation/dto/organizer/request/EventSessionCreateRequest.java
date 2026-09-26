package com.tikitaka.platform.event.presentation.dto.organizer.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Schema(description = "공연 회차 등록 요청")
public record EventSessionCreateRequest(

    @NotNull
    OffsetDateTime performanceStartAt,

    @NotNull
    OffsetDateTime performanceEndAt,

    @NotNull
    OffsetDateTime salesOpenAt,

    @NotNull
    OffsetDateTime salesCloseAt,

    @NotNull
    Boolean queueEnabled
    ) {
}
