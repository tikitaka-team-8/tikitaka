package com.tikitaka.platform.auth.infrastructure.token;

import java.time.Duration;

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
}
