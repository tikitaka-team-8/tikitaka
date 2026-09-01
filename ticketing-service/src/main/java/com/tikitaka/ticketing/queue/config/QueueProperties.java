package com.tikitaka.ticketing.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        Duration admissionTokenTtl,
        Duration enteredEntryTtl,
        Duration waitingFallbackTtl,
        Duration retentionAfterSalesClose,
        Duration usedTokenTtl
) {
}
