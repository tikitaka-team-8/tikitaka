package com.tikitaka.ticketing.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        Duration admissionTokenTtl,
        Duration retentionAfterSalesClose,
        int admissionBatchSize
) {
    public QueueProperties {
        if (admissionBatchSize <= 0) {
            throw new IllegalArgumentException("admissionBatchSize must be positive");
        }
    }
}
