package com.tikitaka.ticketing.queue.domain;

import java.time.Instant;
import java.util.UUID;

public record QueueEntry(
        UUID sessionId,
        long userId,
        QueueStatus status,
        long sequence,
        Instant joinedAt,
        Instant admittedAt,
        Instant expiresAt
) {
    public static QueueEntry waiting(UUID sessionId, long userId, long sequence, Instant joinedAt, Instant expiresAt) {
        return new QueueEntry(sessionId, userId, QueueStatus.WAITING, sequence, joinedAt, null, expiresAt);
    }

    public QueueEntry admit(Instant admittedAt) {
        return new QueueEntry(sessionId, userId, QueueStatus.ADMITTED, sequence, joinedAt, admittedAt, expiresAt);
    }

    public QueueEntry enter() {
        return new QueueEntry(sessionId, userId, QueueStatus.ENTERED, sequence, joinedAt, admittedAt, expiresAt);
    }

    public QueueEntry expire(Instant expiredAt) {
        return new QueueEntry(sessionId, userId, QueueStatus.EXPIRED, sequence, joinedAt, admittedAt, expiredAt);
    }
}
