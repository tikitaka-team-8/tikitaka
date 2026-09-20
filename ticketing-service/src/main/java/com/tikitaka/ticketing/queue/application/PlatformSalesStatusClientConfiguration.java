package com.tikitaka.ticketing.queue.application;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class PlatformSalesStatusClientConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "queue.diagnostics.platform-client", havingValue = "true")
    public PlatformFeignDiagnostics platformFeignDiagnostics(io.micrometer.core.instrument.MeterRegistry registry) {
        return new PlatformFeignDiagnostics(registry);
    }

    private static final String SERVICE_KEY_HEADER = "X-Service-Key";

    @Bean
    public RequestInterceptor platformServiceKeyInterceptor(
            @Value("${clients.platform-service.service-key}") String serviceKey
    ) {
        if (serviceKey.isBlank()) {
            throw new IllegalStateException("Platform 내부 서비스 키는 필수입니다.");
        }

        return requestTemplate -> requestTemplate.header(SERVICE_KEY_HEADER, serviceKey);
    }
}
