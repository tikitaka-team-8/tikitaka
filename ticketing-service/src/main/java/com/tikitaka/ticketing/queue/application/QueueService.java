package com.tikitaka.ticketing.queue.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.global.exception.DownstreamErrorCode;
import com.tikitaka.ticketing.global.response.ApiErrorResponse;
import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import com.tikitaka.ticketing.queue.exception.QueueErrorCode;
import feign.FeignException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

@Service
public class QueueService {
    private final QueueRepository queueRepository;
    private final PlatformSalesStatusClient platformSalesStatusClient;
    private final QueueProperties queueProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public QueueService(
            QueueRepository queueRepository,
            PlatformSalesStatusClient platformSalesStatusClient,
            QueueProperties queueProperties,
            ObjectMapper objectMapper
    ) {
        this(queueRepository, platformSalesStatusClient, queueProperties, Clock.systemUTC(), objectMapper);
    }

    QueueService(
            QueueRepository queueRepository,
            PlatformSalesStatusClient platformSalesStatusClient,
            QueueProperties queueProperties,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.queueRepository = queueRepository;
        this.platformSalesStatusClient = platformSalesStatusClient;
        this.queueProperties = queueProperties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public QueueEntry enterQueue(UUID sessionId, long userId) {
        try {
            return enterQueueInternal(sessionId, userId);
        } catch (RedisConnectionFailureException exception) {
            throw new BusinessException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    private QueueEntry enterQueueInternal(UUID sessionId, long userId) {
        Optional<QueueEntry> existingEntry = queueRepository.findEntry(sessionId, userId);
        if (existingEntry.isPresent() && existingEntry.get().status().isActive()) {
            if (existingEntry.get().status() == QueueStatus.WAITING) {
                queueRepository.registerWaitingSession(sessionId);
            }
            return existingEntry.get();
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
            queueRepository.registerWaitingSession(sessionId);
            return createdEntry.get();
        }

        QueueEntry concurrentEntry = getEntry(sessionId, userId);
        if (concurrentEntry.status().isActive()) {
            if (concurrentEntry.status() == QueueStatus.WAITING) {
                queueRepository.registerWaitingSession(sessionId);
            }
            return concurrentEntry;
        }
        throw new BusinessException(QueueErrorCode.QUEUE_ENTRY_STATE_CONFLICT);
    }

    public QueueEntry getEntry(UUID sessionId, long userId) {
        try {
            return queueRepository.findEntry(sessionId, userId)
                    .orElseThrow(() -> new BusinessException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND));
        } catch (RedisConnectionFailureException exception) {
            throw new BusinessException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    public QueueStatusResult getQueueStatus(UUID sessionId, long userId) {
        try {
            QueueEntry entry = queueRepository.findEntry(sessionId, userId)
                    .orElseThrow(() -> new BusinessException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND));
            Long position = entry.status() == QueueStatus.WAITING
                    ? queueRepository.findWaitingPosition(sessionId, userId).orElse(null)
                    : null;
            AdmissionToken admissionToken = entry.status() == QueueStatus.ADMITTED
                    ? queueRepository.findAdmissionTokenReference(sessionId, userId)
                    .flatMap(token -> queueRepository.findAdmissionToken(sessionId, token))
                    .orElse(null)
                    : null;
            return new QueueStatusResult(entry, position, admissionToken);
        } catch (RedisConnectionFailureException exception) {
            throw new BusinessException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    private PlatformSalesStatus getSalesStatus(UUID sessionId) {
        PlatformSalesStatus salesStatus;
        try {
            salesStatus = platformSalesStatusClient.getSalesStatus(sessionId);
        } catch (FeignException exception) {
            throw mapPlatformException(exception);
        }
        return salesStatus;
    }

    private void validateSellableSession(PlatformSalesStatus salesStatus, Instant now) {
        OffsetDateTime salesOpenAt = salesStatus.salesOpenAt();
        OffsetDateTime salesCloseAt = salesStatus.salesCloseAt();
        boolean saleStarted = salesOpenAt != null && !salesOpenAt.toInstant().isAfter(now);
        boolean saleEnded = salesCloseAt == null || !salesCloseAt.toInstant().isAfter(now);
        boolean scheduled = "SCHEDULED".equalsIgnoreCase(salesStatus.sessionStatus());

        if (!salesStatus.queueEnabled() || !scheduled || !saleStarted || saleEnded) {
            throw new BusinessException(QueueErrorCode.QUEUE_SESSION_NOT_OPEN);
        }
    }

    private BusinessException mapPlatformException(FeignException exception) {
        if (isTimeout(exception)) {
            return new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT);
        }
        return toDownstreamBusinessException(exception)
                .orElseGet(() -> new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE));
    }

    private Optional<BusinessException> toDownstreamBusinessException(FeignException exception) {
        if (exception.status() < HttpStatus.BAD_REQUEST.value()
                || exception.status() >= HttpStatus.INTERNAL_SERVER_ERROR.value()
                || exception.contentUTF8().isBlank()) {
            return Optional.empty();
        }

        try {
            ApiErrorResponse errorResponse = objectMapper.readValue(exception.contentUTF8(), ApiErrorResponse.class);
            HttpStatus status = HttpStatus.resolve(errorResponse.status());
            if (status == null || status.value() != exception.status()
                    || errorResponse.code() == null || errorResponse.message() == null) {
                return Optional.empty();
            }
            return Optional.of(new BusinessException(new DownstreamErrorCode(
                    errorResponse.code(),
                    status,
                    errorResponse.message()
            )));
        } catch (JsonProcessingException parseException) {
            return Optional.empty();
        }
    }

    private boolean isTimeout(FeignException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
