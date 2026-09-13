package com.tikitaka.platform.event.presentation.dto.organizer.request;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

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
