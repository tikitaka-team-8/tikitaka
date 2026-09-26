package com.tikitaka.ticketing.seat.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 좌석 목록 조회 짧은 TTL 캐시 설정.
 *
 * 캐시 on/off와 TTL(초)은 테스트 조건에 따라 자주 바뀌는 값이라 코드 상수가 아니라
 * application.yaml(seat.list.cache.enabled / seat.list.cache.ttl-seconds) 프로퍼티로
 * 관리한다. docker-compose.test.yml에서 이 값들을 환경변수(SEAT_LIST_CACHE_ENABLED /
 * SEAT_LIST_CACHE_TTL_SECONDS)로 오버라이드하면 재빌드 없이 캐시 조건을 바꿔가며
 * 성능 개선 전후를 재현할 수 있다. cacheEnabled가 false면 아무것도 캐싱하지 않는
 * NoOpCacheManager를 등록해서 @Cacheable이 붙어 있어도 매번 실제 조회가 실행되게 하고,
 * true면 Caffeine 기반 CacheManager를 등록해서 ttlSeconds 동안 항목을 유지한다.
 */
@Configuration
public class SeatListCacheConfig {

    private static final String SEAT_LIST_CACHE_NAME = "seatList";

    @Value("${seat.list.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${seat.list.cache.ttl-seconds:2}")
    private long cacheTtlSeconds;

    @Bean
    public CacheManager seatListCacheManager() {
        if (!cacheEnabled) {
            return new NoOpCacheManager();
        }

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SEAT_LIST_CACHE_NAME);
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
                        .maximumSize(10_000)
        );
        return cacheManager;
    }
}
