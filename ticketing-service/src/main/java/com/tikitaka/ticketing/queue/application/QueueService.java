package com.tikitaka.ticketing.queue.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import com.tikitaka.ticketing.queue.exception.QueueErrorCode;
import feign.FeignException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QueueService {
    private final QueueRepository queueRepository;
    private final PlatformSalesStatusClient platformSalesStatusClient;
    private final QueueProperties queueProperties;
    private final Clock clock;

    @Autowired
    public QueueService(
            QueueRepository queueRepository,
            PlatformSalesStatusClient platformSalesStatusClient,
            QueueProperties queueProperties
    ) {
        this(queueRepository, platformSalesStatusClient, queueProperties, Clock.systemUTC());
    }

    QueueService(
            QueueRepository queueRepository,
            PlatformSalesStatusClient platformSalesStatusClient,
            QueueProperties queueProperties,
            Clock clock
    ) {
        this.queueRepository = queueRepository;
        this.platformSalesStatusClient = platformSalesStatusClient;
        this.queueProperties = queueProperties;
        this.clock = clock;
    }

    public QueueCommandResult enterQueue(UUID sessionId, long userId) {
        Optional<QueueEntry> existingEntry = queueRepository.findEntry(sessionId, userId);
        if (existingEntry.isPresent() && existingEntry.get().status().isActive()) {
            return new QueueCommandResult(existingEntry.get(), true);
        }
        if (existingEntry.isPresent() && existingEntry.get().status() == QueueStatus.EXPIRED) {
            queueRepository.deleteEntry(sessionId, userId);
        }

        PlatformSalesStatus salesStatus = getSalesStatus(sessionId);
        Instant now = Instant.now(clock);
        validateSellableSession(salesStatus, now);
        Instant queueExpiresAt = salesStatus.salesCloseAt().toInstant()
                .plus(queueProperties.retentionAfterSalesClose());
        Optional<QueueEntry> createdEntry = queueRepository.createWaitingEntryIfAbsent(
                sessionId,
                userId,
                now,
                queueExpiresAt,
                Duration.between(now, queueExpiresAt)
        );
        if (createdEntry.isPresent()) {
            return new QueueCommandResult(createdEntry.get(), false);
        }

        QueueEntry concurrentEntry = getEntry(sessionId, userId);
        if (concurrentEntry.status().isActive()) {
            return new QueueCommandResult(concurrentEntry, true);
        }
        throw new BusinessException(QueueErrorCode.QUEUE_ENTRY_STATE_CONFLICT);
    }

    public QueueEntry getEntry(UUID sessionId, long userId) {
        return queueRepository.findEntry(sessionId, userId)
                .orElseThrow(() -> new BusinessException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND));
    }

    private PlatformSalesStatus getSalesStatus(UUID sessionId) {
        PlatformSalesStatus salesStatus;
        try {
            salesStatus = platformSalesStatusClient.getSalesStatus(sessionId);
        } catch (FeignException exception) {
            throw new BusinessException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        return salesStatus;
    }

    private void validateSellableSession(PlatformSalesStatus salesStatus, Instant now) {
        OffsetDateTime salesOpenAt = salesStatus.salesOpenAt();
        OffsetDateTime salesCloseAt = salesStatus.salesCloseAt();
        boolean saleStarted = salesOpenAt != null && !salesOpenAt.toInstant().isAfter(now);
        boolean saleEnded = salesCloseAt == null || !salesCloseAt.toInstant().isAfter(now);
        boolean onSale = "ON_SALE".equalsIgnoreCase(salesStatus.sessionStatus());

        if (!salesStatus.queueEnabled() || !onSale || !saleStarted || saleEnded) {
            throw new BusinessException(QueueErrorCode.QUEUE_SESSION_NOT_OPEN);
        }
    }
}
