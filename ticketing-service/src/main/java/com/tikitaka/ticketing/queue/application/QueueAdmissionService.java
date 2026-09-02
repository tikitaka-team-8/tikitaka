package com.tikitaka.ticketing.queue.application;

import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.AdmissionTokenStatus;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

@Service
public class QueueAdmissionService {
    private static final Logger log = LoggerFactory.getLogger(QueueAdmissionService.class);

    private final QueueRepository queueRepository;
    private final QueueProperties queueProperties;
    private final Clock clock;

    public QueueAdmissionService(QueueRepository queueRepository, QueueProperties queueProperties, Clock clock) {
        this.queueRepository = queueRepository;
        this.queueProperties = queueProperties;
        this.clock = clock;
    }

    public void admitWaitingUsers() {
        try {
            for (UUID sessionId : queueRepository.findWaitingSessionIds()) {
                try {
                    admitWaitingUsers(sessionId);
                } catch (RedisConnectionFailureException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    log.error("Queue admission failed for sessionId={}", sessionId, exception);
                }
            }
        } catch (RedisConnectionFailureException exception) {
            log.warn("Queue admission skipped because Redis is unavailable", exception);
        }
    }

    private void admitWaitingUsers(UUID sessionId) {
        var waitingEntries = queueRepository.findWaitingEntries(sessionId, queueProperties.admissionBatchSize());
        if (waitingEntries.isEmpty()) {
            queueRepository.removeWaitingSession(sessionId);
            return;
        }

        for (QueueEntry entry : waitingEntries) {
            try {
                admitIfWaiting(entry);
            } catch (RedisConnectionFailureException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.error(
                        "Queue admission failed for sessionId={}, userId={}",
                        entry.sessionId(),
                        entry.userId(),
                        exception
                );
            }
        }
    }

    private void admitIfWaiting(QueueEntry entry) {
        if (entry.status() != QueueStatus.WAITING || entry.expiresAt() == null) {
            return;
        }

        Instant now = Instant.now(clock);
        if (!entry.expiresAt().isAfter(now)) {
            queueRepository.removeWaitingUser(entry.sessionId(), entry.userId());
            queueRepository.deleteEntry(entry.sessionId(), entry.userId());
            return;
        }

        Instant admissionExpiresAt = now.plus(queueProperties.admissionTokenTtl());
        AdmissionToken token = new AdmissionToken(
                UUID.randomUUID().toString(),
                entry.sessionId(),
                entry.userId(),
                admissionExpiresAt,
                AdmissionTokenStatus.ACTIVE
        );
        Duration sessionTtl = Duration.between(now, entry.expiresAt());
        queueRepository.admitIfWaiting(
                entry.admit(now),
                token,
                sessionTtl,
                queueProperties.admissionTokenTtl()
        );
    }
}
