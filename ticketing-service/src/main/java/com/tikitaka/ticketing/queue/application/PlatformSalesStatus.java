package com.tikitaka.ticketing.queue.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformSalesStatus(
        UUID sessionId,
        String sessionStatus,
        OffsetDateTime salesOpenAt,
        OffsetDateTime salesCloseAt,
        boolean queueEnabled
) {
}
