package com.tikitaka.ticketing.queue.presentation;

import com.tikitaka.ticketing.queue.application.QueueStatusResult;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import java.time.Instant;
import java.util.UUID;

public record QueueEntryResponse(
        UUID sessionId,
        long userId,
        QueueStatus status,
        Long position,
        Instant joinedAt,
        Instant admittedAt,
        Instant expiresAt,
        String admissionToken
) {
    public static QueueEntryResponse from(QueueStatusResult result) {
        QueueEntry entry = result.entry();
        AdmissionToken admissionToken = result.admissionToken();
        return new QueueEntryResponse(
                entry.sessionId(),
                entry.userId(),
                entry.status(),
                result.position(),
                entry.joinedAt(),
                entry.admittedAt(),
                admissionToken == null ? entry.expiresAt() : admissionToken.expiresAt(),
                admissionToken == null ? null : admissionToken.token()
        );
    }
}
