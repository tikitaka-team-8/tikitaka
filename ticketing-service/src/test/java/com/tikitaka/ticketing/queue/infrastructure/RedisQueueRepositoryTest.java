package com.tikitaka.ticketing.queue.infrastructure;

import com.tikitaka.ticketing.queue.application.QueueRepository;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import(RedisQueueRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class RedisQueueRepositoryTest {
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2.14-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Test
    void createWaitingEntryIfAbsentCreatesOnlyOneEntryAndExpiresSessionKeys() {
        UUID sessionId = UUID.randomUUID();
        long userId = 100L;
        Instant joinedAt = Instant.parse("2026-09-01T01:00:00Z");

        QueueEntry createdEntry = queueRepository.createWaitingEntryIfAbsent(
                        sessionId, userId, joinedAt, joinedAt.plus(SESSION_TTL), SESSION_TTL)
                .orElseThrow();
        boolean createdAgain = queueRepository.createWaitingEntryIfAbsent(
                sessionId, userId, joinedAt.plusSeconds(1), joinedAt.plus(SESSION_TTL), SESSION_TTL).isPresent();

        assertThat(createdEntry.sequence()).isEqualTo(1L);
        assertThat(createdAgain).isFalse();
        assertThat(queueRepository.findEntry(sessionId, userId)).contains(createdEntry);
        assertThat(redisTemplate.getExpire("queue:waiting:{" + sessionId + "}")).isPositive();
        assertThat(redisTemplate.getExpire("queue:sequence:{" + sessionId + "}")).isPositive();
    }

    @Test
    void addActiveUserExpiresTheSessionActiveIndex() {
        UUID sessionId = UUID.randomUUID();
        queueRepository.createWaitingEntryIfAbsent(
                sessionId, 100L, Instant.parse("2026-09-01T01:00:00Z"),
                Instant.parse("2026-09-01T03:00:00Z"), SESSION_TTL);

        queueRepository.addActiveUser(
                sessionId, 100L, Instant.parse("2026-09-01T01:10:00Z"), SESSION_TTL);

        assertThat(redisTemplate.getExpire("queue:active:{" + sessionId + "}")).isPositive();
    }

    @Test
    void saveEntryPreservesTheExistingEntryTtl() {
        UUID sessionId = UUID.randomUUID();
        long userId = 100L;
        QueueEntry waitingEntry = queueRepository.createWaitingEntryIfAbsent(
                        sessionId, userId, Instant.parse("2026-09-01T01:00:00Z"),
                        Instant.parse("2026-09-01T03:00:00Z"), SESSION_TTL)
                .orElseThrow();
        String entryKey = "queue:entry:{" + sessionId + "}:" + userId;
        long ttlBefore = redisTemplate.getExpire(entryKey, TimeUnit.MILLISECONDS);

        boolean updated = queueRepository.updateEntryIfPresent(
                waitingEntry.admit(Instant.parse("2026-09-01T01:01:00Z")));

        long ttlAfter = redisTemplate.getExpire(entryKey, TimeUnit.MILLISECONDS);
        assertThat(updated).isTrue();
        assertThat(ttlAfter).isPositive().isLessThanOrEqualTo(ttlBefore);
    }

    @Test
    void updateEntryIfPresentDoesNotRecreateAnExpiredEntry() {
        QueueEntry expiredEntry = QueueEntry.waiting(
                UUID.randomUUID(), 100L, 1L,
                Instant.parse("2026-09-01T01:00:00Z"),
                Instant.parse("2026-09-01T03:00:00Z")
        );

        boolean updated = queueRepository.updateEntryIfPresent(expiredEntry.admit(
                Instant.parse("2026-09-01T01:01:00Z")
        ));

        assertThat(updated).isFalse();
        assertThat(queueRepository.findEntry(expiredEntry.sessionId(), expiredEntry.userId())).isEmpty();
    }
}
