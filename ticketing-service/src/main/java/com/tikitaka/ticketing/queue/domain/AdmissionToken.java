package com.tikitaka.ticketing.queue.domain;

import java.time.Instant;
import java.util.UUID;

public record AdmissionToken(
        String token,
        UUID sessionId,
        long userId,
        Instant expiresAt,
        AdmissionTokenStatus status
) {
    public AdmissionToken use() {
        ensureActive();
        return new AdmissionToken(token, sessionId, userId, expiresAt, AdmissionTokenStatus.USED);
    }

    public AdmissionToken expire() {
        ensureActive();
        return new AdmissionToken(token, sessionId, userId, expiresAt, AdmissionTokenStatus.EXPIRED);
    }

    private void ensureActive() {
        if (status != AdmissionTokenStatus.ACTIVE) {
            throw new IllegalStateException("Only active admission tokens can transition");
        }
    }
}
