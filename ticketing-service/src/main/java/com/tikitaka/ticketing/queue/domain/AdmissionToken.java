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
        return new AdmissionToken(token, sessionId, userId, expiresAt, AdmissionTokenStatus.USED);
    }

    public AdmissionToken expire() {
        return new AdmissionToken(token, sessionId, userId, expiresAt, AdmissionTokenStatus.EXPIRED);
    }
}
