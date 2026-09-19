package com.tikitaka.ticketing.seat.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 좌석 목록 조회 "짧은 TTL 캐시" 실험용 설정.
 *
 * 외부 설정(application.yaml/@ConfigurationProperties)에 기대지 않고, 아래 상수를 코드에서
 * 직접 바꿔서 켜고 끄는 방식이다. CACHE_ENABLED가 false(기본)면 아무것도 캐싱하지 않는
 * NoOpCacheManager를 등록해서 @Cacheable이 붙어 있어도 매번 실제 조회가 실행되게 하고,
 * true면 Caffeine 기반 CacheManager를 등록해서 CACHE_TTL_SECONDS 동안 항목을 유지한다.
 * 값을 바꾼 뒤에는 재빌드·재기동해야 반영된다.
 */
@Configuration
public class SeatListCacheConfig {

    private static final String SEAT_LIST_CACHE_NAME = "seatList";

    // 캐시 on/off 및 TTL(초) 실험용 토글.
    private static final boolean CACHE_ENABLED = true;
    private static final long CACHE_TTL_SECONDS = 2;

    @Bean
    public CacheManager seatListCacheManager() {
        if (!CACHE_ENABLED) {
            return new NoOpCacheManager();
        }

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SEAT_LIST_CACHE_NAME);
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                        .maximumSize(10_000)
        );
        return cacheManager;
    }
}
