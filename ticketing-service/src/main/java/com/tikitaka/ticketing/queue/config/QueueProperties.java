package com.tikitaka.ticketing.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        Duration admissionTokenTtl,
        Duration retentionAfterSalesClose,
        int admissionBatchSize,
        int expirationBatchSize
) {
    public QueueProperties {
        if (admissionBatchSize <= 0 || expirationBatchSize <= 0) {
            throw new IllegalArgumentException("Queue batch sizes must be positive");
        }
    }
}
