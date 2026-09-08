package com.tikitaka.ticketing.queue.presentation;

import java.util.UUID;

public record QueueLeaveResponse(
        UUID sessionId,
        String queueStatus
) {
    public static QueueLeaveResponse left(UUID sessionId) {
        return new QueueLeaveResponse(sessionId, "LEFT");
    }
}
