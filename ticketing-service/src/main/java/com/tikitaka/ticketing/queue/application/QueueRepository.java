package com.tikitaka.ticketing.queue.application;

import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.QueueEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface QueueRepository {

    Optional<QueueEntry> findEntry(UUID sessionId, long userId);

    List<QueueEntry> findWaitingEntries(UUID sessionId, int limit);

    Optional<Long> findWaitingPosition(UUID sessionId, long userId);

    Set<UUID> findWaitingSessionIds();

    Set<UUID> findActiveSessionIds();

    void registerWaitingSession(UUID sessionId);

    boolean removeActiveSessionIfEmpty(UUID sessionId);

    boolean removeWaitingSessionIfEmpty(UUID sessionId);

    Optional<AdmissionToken> findAdmissionToken(UUID sessionId, String token);

    Optional<String> findAdmissionTokenReference(UUID sessionId, long userId);

    Optional<QueueEntry> createWaitingEntryIfAbsent(
            UUID sessionId,
            long userId,
            Instant joinedAt,
            Instant expiresAt,
            Duration sessionTtl
    );

    boolean updateEntryIfPresent(QueueEntry entry);

    boolean admitIfWaiting(
            QueueEntry admittedEntry,
            AdmissionToken admissionToken,
            Duration sessionTtl,
            Duration admissionTokenTtl
    );

    boolean enterIfAdmissionTokenActive(QueueEntry enteredEntry, AdmissionToken admissionToken);

    List<QueueEntry> findExpiredAdmittedEntries(UUID sessionId, Instant now, int limit);

    boolean expireIfAdmitted(QueueEntry expiredEntry, Instant now);

    void removeWaitingUser(UUID sessionId, long userId);

    void removeActiveUser(UUID sessionId, long userId);

    boolean updateAdmissionTokenIfPresent(AdmissionToken admissionToken);

    void deleteAdmissionToken(UUID sessionId, String token);

    void deleteAdmissionTokenReference(UUID sessionId, long userId);

    void deleteEntry(UUID sessionId, long userId);
}
