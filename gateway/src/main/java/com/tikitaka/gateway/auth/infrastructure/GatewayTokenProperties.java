package com.tikitaka.gateway.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.token")
public record GatewayTokenProperties(
        String secret
) {
}
