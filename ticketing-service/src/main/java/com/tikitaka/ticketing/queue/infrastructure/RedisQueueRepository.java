package com.tikitaka.ticketing.queue.infrastructure;

import com.tikitaka.ticketing.queue.application.QueueRepository;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.AdmissionTokenStatus;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public class RedisQueueRepository implements QueueRepository {
    private static final String STATUS_FIELD = "status";
    private static final String SEQUENCE_FIELD = "sequence";
    private static final String JOINED_AT_FIELD = "joinedAt";
    private static final String ADMITTED_AT_FIELD = "admittedAt";
    private static final String EXPIRES_AT_FIELD = "expiresAt";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String USER_ID_FIELD = "userId";
    private static final String TOKEN_STATUS_FIELD = "status";
    private static final DefaultRedisScript<Long> CREATE_WAITING_ENTRY_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                        return 0
                    end

                    local sequence = redis.call('INCR', KEYS[3])
                    redis.call('HSET', KEYS[1],
                        'status', 'WAITING',
                        'sequence', sequence,
                        'joinedAt', ARGV[2],
                        'expiresAt', ARGV[3])
                    redis.call('PEXPIRE', KEYS[1], ARGV[4])
                    redis.call('ZADD', KEYS[2], sequence, ARGV[1])
                    redis.call('PEXPIRE', KEYS[2], ARGV[4])
                    redis.call('PEXPIRE', KEYS[3], ARGV[4])
                    return sequence
                    """,
            Long.class
    );
    private static final DefaultRedisScript<Long> UPDATE_ENTRY_IF_PRESENT_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 0
                    end

                    redis.call('HSET', KEYS[1],
                        'status', ARGV[1],
                        'sequence', ARGV[2],
                        'joinedAt', ARGV[3])

                    if ARGV[4] == '' then
                        redis.call('HDEL', KEYS[1], 'admittedAt')
                    else
                        redis.call('HSET', KEYS[1], 'admittedAt', ARGV[4])
                    end

                    if ARGV[5] == '' then
                        redis.call('HDEL', KEYS[1], 'expiresAt')
                    else
                        redis.call('HSET', KEYS[1], 'expiresAt', ARGV[5])
                    end
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisQueueRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<QueueEntry> findEntry(UUID sessionId, long userId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(entryKey(sessionId, userId));
        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new QueueEntry(
                sessionId,
                userId,
                QueueStatus.valueOf(requiredValue(values, STATUS_FIELD)),
                Long.parseLong(requiredValue(values, SEQUENCE_FIELD)),
                Instant.parse(requiredValue(values, JOINED_AT_FIELD)),
                optionalInstant(values.get(ADMITTED_AT_FIELD)),
                optionalInstant(values.get(EXPIRES_AT_FIELD))
        ));
    }

    @Override
    public Optional<AdmissionToken> findAdmissionToken(String token) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(admissionTokenKey(token));
        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new AdmissionToken(
                token,
                UUID.fromString(requiredValue(values, SESSION_ID_FIELD)),
                Long.parseLong(requiredValue(values, USER_ID_FIELD)),
                Instant.parse(requiredValue(values, EXPIRES_AT_FIELD)),
                AdmissionTokenStatus.valueOf(requiredValue(values, TOKEN_STATUS_FIELD))
        ));
    }

    @Override
    public Optional<String> findAdmissionTokenReference(UUID sessionId, long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(admissionTokenReferenceKey(sessionId, userId)));
    }

    @Override
    public Optional<QueueEntry> createWaitingEntryIfAbsent(
            UUID sessionId,
            long userId,
            Instant joinedAt,
            Instant expiresAt,
            Duration sessionTtl
    ) {
        Long sequence = redisTemplate.execute(
                CREATE_WAITING_ENTRY_SCRIPT,
                List.of(entryKey(sessionId, userId), waitingKey(sessionId), sequenceKey(sessionId)),
                String.valueOf(userId),
                joinedAt.toString(),
                expiresAt.toString(),
                String.valueOf(sessionTtl.toMillis())
        );
        if (sequence == null || sequence == 0L) {
            return Optional.empty();
        }
        return Optional.of(QueueEntry.waiting(sessionId, userId, sequence, joinedAt, expiresAt));
    }

    @Override
    public boolean updateEntryIfPresent(QueueEntry entry) {
        Long updated = redisTemplate.execute(
                UPDATE_ENTRY_IF_PRESENT_SCRIPT,
                List.of(entryKey(entry.sessionId(), entry.userId())),
                entry.status().name(),
                String.valueOf(entry.sequence()),
                entry.joinedAt().toString(),
                optionalInstantValue(entry.admittedAt()),
                optionalInstantValue(entry.expiresAt())
        );
        return updated != null && updated == 1L;
    }

    @Override
    public void removeWaitingUser(UUID sessionId, long userId) {
        redisTemplate.opsForZSet().remove(waitingKey(sessionId), String.valueOf(userId));
    }

    @Override
    public void addActiveUser(UUID sessionId, long userId, Instant expiresAt, Duration sessionTtl) {
        String key = activeKey(sessionId);
        redisTemplate.opsForZSet().add(key, String.valueOf(userId), expiresAt.toEpochMilli());
        redisTemplate.expire(key, sessionTtl);
    }

    @Override
    public void removeActiveUser(UUID sessionId, long userId) {
        redisTemplate.opsForZSet().remove(activeKey(sessionId), String.valueOf(userId));
    }

    @Override
    public void createAdmissionToken(AdmissionToken admissionToken, Duration ttl) {
        String key = admissionTokenKey(admissionToken.token());
        saveAdmissionTokenFields(key, admissionToken);
        redisTemplate.expire(key, ttl);
    }

    @Override
    public void updateAdmissionToken(AdmissionToken admissionToken) {
        saveAdmissionTokenFields(admissionTokenKey(admissionToken.token()), admissionToken);
    }

    private void saveAdmissionTokenFields(String key, AdmissionToken admissionToken) {
        redisTemplate.opsForHash().put(key, SESSION_ID_FIELD, admissionToken.sessionId().toString());
        redisTemplate.opsForHash().put(key, USER_ID_FIELD, String.valueOf(admissionToken.userId()));
        redisTemplate.opsForHash().put(key, EXPIRES_AT_FIELD, admissionToken.expiresAt().toString());
        redisTemplate.opsForHash().put(key, TOKEN_STATUS_FIELD, admissionToken.status().name());
    }

    @Override
    public void saveAdmissionTokenReference(UUID sessionId, long userId, String token, Duration ttl) {
        String key = admissionTokenReferenceKey(sessionId, userId);
        redisTemplate.opsForValue().set(key, token, ttl);
    }

    @Override
    public void deleteAdmissionToken(String token) {
        redisTemplate.delete(admissionTokenKey(token));
    }

    @Override
    public void deleteAdmissionTokenReference(UUID sessionId, long userId) {
        redisTemplate.delete(admissionTokenReferenceKey(sessionId, userId));
    }

    @Override
    public void deleteEntry(UUID sessionId, long userId) {
        redisTemplate.delete(entryKey(sessionId, userId));
    }

    private String requiredValue(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            throw new IllegalStateException("Missing redis hash field: " + field);
        }
        return value.toString();
    }

    private Instant optionalInstant(Object value) {
        return value == null ? null : Instant.parse(value.toString());
    }

    private String optionalInstantValue(Instant value) {
        return value == null ? "" : value.toString();
    }

    private String sequenceKey(UUID sessionId) {
        return "queue:sequence:{" + sessionId + "}";
    }

    private String waitingKey(UUID sessionId) {
        return "queue:waiting:{" + sessionId + "}";
    }

    private String entryKey(UUID sessionId, long userId) {
        return "queue:entry:{" + sessionId + "}:" + userId;
    }

    private String activeKey(UUID sessionId) {
        return "queue:active:{" + sessionId + "}";
    }

    private String admissionTokenKey(String token) {
        return "queue:admission-token:" + token;
    }

    private String admissionTokenReferenceKey(UUID sessionId, long userId) {
        return "queue:admission-token-ref:{" + sessionId + "}:" + userId;
    }
}
