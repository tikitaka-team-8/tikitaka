package com.tikitaka.ticketing.queue.presentation;

import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import java.time.Instant;
import java.util.UUID;

public record QueueEntryResponse(
        UUID sessionId,
        long userId,
        QueueStatus status,
        long sequence,
        Instant joinedAt,
        Instant admittedAt,
        Instant expiresAt
) {
    public static QueueEntryResponse from(QueueEntry entry) {
        return new QueueEntryResponse(
                entry.sessionId(),
                entry.userId(),
                entry.status(),
                entry.sequence(),
                entry.joinedAt(),
                entry.admittedAt(),
                entry.expiresAt()
        );
    }
}
