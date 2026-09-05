package com.tikitaka.platform.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.service")
public record InternalServiceKeyProperties(String key) {

    public InternalServiceKeyProperties {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("내부 서비스 키는 필수입니다.");
        }
    }
}
