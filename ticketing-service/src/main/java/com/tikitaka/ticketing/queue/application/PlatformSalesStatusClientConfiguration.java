package com.tikitaka.ticketing.queue.application;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class PlatformSalesStatusClientConfiguration {

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
