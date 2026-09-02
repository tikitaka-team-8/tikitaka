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
import java.util.Set;
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
    private static final DefaultRedisScript<Long> ADMIT_IF_WAITING_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('EXISTS', KEYS[1]) == 0
                        or redis.call('HGET', KEYS[1], 'status') ~= 'WAITING' then
                        return 0
                    end

                    redis.call('HSET', KEYS[1],
                        'status', 'ADMITTED',
                        'sequence', ARGV[1],
                        'joinedAt', ARGV[2],
                        'admittedAt', ARGV[3],
                        'expiresAt', ARGV[4])
                    redis.call('ZREM', KEYS[2], ARGV[5])
                    redis.call('ZADD', KEYS[3], ARGV[6], ARGV[5])
                    redis.call('PEXPIRE', KEYS[3], ARGV[10])
                    redis.call('HSET', KEYS[4],
                        'sessionId', ARGV[7],
                        'userId', ARGV[5],
                        'expiresAt', ARGV[8],
                        'status', 'ACTIVE')
                    redis.call('PEXPIRE', KEYS[4], ARGV[9])
                    redis.call('SET', KEYS[5], ARGV[11], 'PX', ARGV[9])
                    return 1
                    """,
            Long.class
    );
    private static final DefaultRedisScript<Long> ENTER_IF_ADMISSION_TOKEN_ACTIVE_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('EXISTS', KEYS[1]) == 0
                        or redis.call('EXISTS', KEYS[3]) == 0
                        or redis.call('HGET', KEYS[1], 'status') ~= 'ADMITTED'
                        or redis.call('HGET', KEYS[3], 'status') ~= 'ACTIVE' then
                        return 0
                    end

                    redis.call('HSET', KEYS[1],
                        'status', 'ENTERED',
                        'sequence', ARGV[1],
                        'joinedAt', ARGV[2],
                        'admittedAt', ARGV[3],
                        'expiresAt', ARGV[4])
                    redis.call('ZREM', KEYS[2], ARGV[5])
                    redis.call('HSET', KEYS[3], 'status', 'USED')
                    return 1
                    """,
            Long.class
    );
    private static final DefaultRedisScript<Long> UPDATE_ADMISSION_TOKEN_IF_PRESENT_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 0
                    end

                    redis.call('HSET', KEYS[1],
                        'sessionId', ARGV[1],
                        'userId', ARGV[2],
                        'expiresAt', ARGV[3],
                        'status', ARGV[4])
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
    public List<QueueEntry> findWaitingEntries(UUID sessionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        var userIds = redisTemplate.opsForZSet().range(waitingKey(sessionId), 0, limit - 1L);
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream()
                .map(Long::parseLong)
                .map(userId -> findEntry(sessionId, userId))
                .flatMap(Optional::stream)
                .filter(entry -> entry.status() == QueueStatus.WAITING)
                .toList();
    }

    @Override
    public Optional<Long> findWaitingPosition(UUID sessionId, long userId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(sessionId), String.valueOf(userId));
        return rank == null ? Optional.empty() : Optional.of(rank + 1);
    }

    @Override
    public Set<UUID> findWaitingSessionIds() {
        Set<String> sessionIds = redisTemplate.opsForSet().members(waitingSessionRegistryKey());
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Set.of();
        }
        return sessionIds.stream().map(UUID::fromString).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public void registerWaitingSession(UUID sessionId) {
        redisTemplate.opsForSet().add(waitingSessionRegistryKey(), sessionId.toString());
    }

    @Override
    public void removeWaitingSession(UUID sessionId) {
        redisTemplate.opsForSet().remove(waitingSessionRegistryKey(), sessionId.toString());
    }

    @Override
    public Optional<AdmissionToken> findAdmissionToken(UUID sessionId, String token) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(admissionTokenKey(sessionId, token));
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
    public boolean admitIfWaiting(
            QueueEntry admittedEntry,
            AdmissionToken admissionToken,
            Duration sessionTtl,
            Duration admissionTokenTtl
    ) {
        Long admitted = redisTemplate.execute(
                ADMIT_IF_WAITING_SCRIPT,
                List.of(
                        entryKey(admittedEntry.sessionId(), admittedEntry.userId()),
                        waitingKey(admittedEntry.sessionId()),
                        activeKey(admittedEntry.sessionId()),
                        admissionTokenKey(admittedEntry.sessionId(), admissionToken.token()),
                        admissionTokenReferenceKey(admittedEntry.sessionId(), admittedEntry.userId())
                ),
                String.valueOf(admittedEntry.sequence()),
                admittedEntry.joinedAt().toString(),
                admittedEntry.admittedAt().toString(),
                admittedEntry.expiresAt().toString(),
                String.valueOf(admittedEntry.userId()),
                String.valueOf(admissionToken.expiresAt().toEpochMilli()),
                admittedEntry.sessionId().toString(),
                admissionToken.expiresAt().toString(),
                String.valueOf(admissionTokenTtl.toMillis()),
                String.valueOf(sessionTtl.toMillis()),
                admissionToken.token()
        );
        return admitted != null && admitted == 1L;
    }

    @Override
    public boolean enterIfAdmissionTokenActive(QueueEntry enteredEntry, AdmissionToken admissionToken) {
        Long entered = redisTemplate.execute(
                ENTER_IF_ADMISSION_TOKEN_ACTIVE_SCRIPT,
                List.of(
                        entryKey(enteredEntry.sessionId(), enteredEntry.userId()),
                        activeKey(enteredEntry.sessionId()),
                        admissionTokenKey(enteredEntry.sessionId(), admissionToken.token())
                ),
                String.valueOf(enteredEntry.sequence()),
                enteredEntry.joinedAt().toString(),
                optionalInstantValue(enteredEntry.admittedAt()),
                optionalInstantValue(enteredEntry.expiresAt()),
                String.valueOf(enteredEntry.userId())
        );
        return entered != null && entered == 1L;
    }

    @Override
    public void removeWaitingUser(UUID sessionId, long userId) {
        redisTemplate.opsForZSet().remove(waitingKey(sessionId), String.valueOf(userId));
        Long waitingUserCount = redisTemplate.opsForZSet().size(waitingKey(sessionId));
        if (waitingUserCount == null || waitingUserCount == 0L) {
            removeWaitingSession(sessionId);
        }
    }

    @Override
    public void removeActiveUser(UUID sessionId, long userId) {
        redisTemplate.opsForZSet().remove(activeKey(sessionId), String.valueOf(userId));
    }

    @Override
    public boolean updateAdmissionTokenIfPresent(AdmissionToken admissionToken) {
        Long updated = redisTemplate.execute(
                UPDATE_ADMISSION_TOKEN_IF_PRESENT_SCRIPT,
                List.of(admissionTokenKey(admissionToken.sessionId(), admissionToken.token())),
                admissionToken.sessionId().toString(),
                String.valueOf(admissionToken.userId()),
                admissionToken.expiresAt().toString(),
                admissionToken.status().name()
        );
        return updated != null && updated == 1L;
    }

    @Override
    public void deleteAdmissionToken(UUID sessionId, String token) {
        redisTemplate.delete(admissionTokenKey(sessionId, token));
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

    private String waitingSessionRegistryKey() {
        return "queue:waiting-sessions";
    }

    private String entryKey(UUID sessionId, long userId) {
        return "queue:entry:{" + sessionId + "}:" + userId;
    }

    private String activeKey(UUID sessionId) {
        return "queue:active:{" + sessionId + "}";
    }

    private String admissionTokenKey(UUID sessionId, String token) {
        return "queue:admission-token:{" + sessionId + "}:" + token;
    }

    private String admissionTokenReferenceKey(UUID sessionId, long userId) {
        return "queue:admission-token-ref:{" + sessionId + "}:" + userId;
    }
}
