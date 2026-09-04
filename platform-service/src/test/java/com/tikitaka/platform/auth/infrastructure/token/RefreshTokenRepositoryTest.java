package com.tikitaka.platform.auth.infrastructure.token;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void Refresh_Token을_저장할_때_사용자별_인덱스도_저장한다() {
        Long userId = 1L;
        String tokenHash = "token-hash";
        Duration expiration = Duration.ofSeconds(3600);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        refreshTokenRepository.save(userId, tokenHash, expiration.toSeconds());

        then(valueOperations).should().set(
                "auth:refresh-token:token-hash",
                "1",
                expiration
        );
        then(setOperations).should().add(
                "auth:user-refresh-tokens:1",
                tokenHash
        );
        then(redisTemplate).should().expire(
                "auth:user-refresh-tokens:1",
                expiration
        );
    }

    @Test
    void 소유한_Refresh_Token을_삭제하면_사용자별_인덱스에서도_제거한다() {
        Long userId = 1L;
        String tokenHash = "token-hash";
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(valueOperations.get("auth:refresh-token:token-hash"))
                .willReturn("1");

        refreshTokenRepository.deleteIfOwnedBy(userId, tokenHash);

        then(redisTemplate).should().delete("auth:refresh-token:token-hash");
        then(setOperations).should().remove(
                "auth:user-refresh-tokens:1",
                tokenHash
        );
    }

    @Test
    void 사용자의_모든_Refresh_Token을_삭제한다() {
        Long userId = 1L;
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members("auth:user-refresh-tokens:1"))
                .willReturn(new LinkedHashSet<>(List.of(
                        "token-hash-1",
                        "token-hash-2"
                )));

        refreshTokenRepository.deleteAllByUserId(userId);

        then(redisTemplate).should().delete(List.of(
                "auth:refresh-token:token-hash-1",
                "auth:refresh-token:token-hash-2"
        ));
        then(redisTemplate).should().delete("auth:user-refresh-tokens:1");
    }
}
