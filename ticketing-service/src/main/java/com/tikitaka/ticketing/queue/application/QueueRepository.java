package com.tikitaka.ticketing.queue.application;

import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.QueueEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface QueueRepository {

    Optional<QueueEntry> findEntry(UUID sessionId, long userId);

    Optional<AdmissionToken> findAdmissionToken(String token);

    Optional<String> findAdmissionTokenReference(UUID sessionId, long userId);

    Optional<QueueEntry> createWaitingEntryIfAbsent(
            UUID sessionId,
            long userId,
            Instant joinedAt,
            Duration entryTtl,
            Duration sessionTtl
    );

    void saveEntry(QueueEntry entry);

    void removeWaitingUser(UUID sessionId, long userId);

    void addActiveUser(UUID sessionId, long userId, Instant expiresAt);

    void removeActiveUser(UUID sessionId, long userId);

    void createAdmissionToken(AdmissionToken admissionToken, Duration ttl);

    void updateAdmissionToken(AdmissionToken admissionToken);

    void saveAdmissionTokenReference(UUID sessionId, long userId, String token, Duration ttl);

    void deleteAdmissionToken(String token);

    void deleteAdmissionTokenReference(UUID sessionId, long userId);

    void deleteEntry(UUID sessionId, long userId);
}
