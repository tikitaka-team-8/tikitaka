package com.tikitaka.platform.auth.infrastructure.token;

import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh-token:";

    private final StringRedisTemplate redisTemplate;

    public void save(
            Long userId,
            String tokenHash,
            long expirationSeconds
    ) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + tokenHash,
                userId.toString(),
                Duration.ofSeconds(expirationSeconds)
        );
    }

    public Optional<Long> findUserId(String tokenHash) {
        String userId = redisTemplate.opsForValue().get(createKey(tokenHash));

        if (userId == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.valueOf(userId));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Refresh Token 사용자 정보가 올바르지 않습니다.", exception);
        }
    }

    public void deleteIfOwnedBy(Long userId, String tokenHash) {
        findUserId(tokenHash)
                .filter(userId::equals)
                .ifPresent(ignored -> redisTemplate.delete(createKey(tokenHash)));
    }

    private String createKey(String tokenHash) {
        return REFRESH_TOKEN_KEY_PREFIX + tokenHash;
    }
}
