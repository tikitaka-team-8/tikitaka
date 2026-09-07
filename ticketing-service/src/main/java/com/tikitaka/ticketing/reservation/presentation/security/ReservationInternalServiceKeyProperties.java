package com.tikitaka.ticketing.reservation.presentation.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.service")
public record ReservationInternalServiceKeyProperties(String key) {

    public ReservationInternalServiceKeyProperties {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Ticketing 내부 서비스 키는 필수입니다.");
        }
    }
}
