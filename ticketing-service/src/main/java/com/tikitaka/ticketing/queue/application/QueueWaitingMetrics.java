package com.tikitaka.ticketing.queue.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class QueueWaitingMetrics {
    private static final Logger log = LoggerFactory.getLogger(QueueWaitingMetrics.class);
    private final QueueRepository repository;
    // Private scheduler: observation must not occupy the admission scheduler thread.
    private final ThreadPoolTaskScheduler sampler = new ThreadPoolTaskScheduler();
    private volatile double waitingCount = Double.NaN;
    private volatile double sampleSuccess;

    public QueueWaitingMetrics(QueueRepository repository, MeterRegistry registry) {
        this.repository = repository;
        Gauge.builder("queue.waiting.count", this, metrics -> metrics.waitingCount)
                .description("Sampled Redis waiting ZSET members across registered sessions; not an atomic snapshot")
                .register(registry);
        Gauge.builder("queue.waiting.sample.success", this, metrics -> metrics.sampleSuccess)
                .description("1 if the latest waiting count sample succeeded, otherwise 0")
                .register(registry);
    }

    @PostConstruct
    public void start() {
        sampler.setThreadNamePrefix("queue-metrics-");
        sampler.setPoolSize(1);
        sampler.initialize();
        sampler.scheduleWithFixedDelay(this::sample, Duration.ofSeconds(5));
    }

    void sample() {
        try {
            long total = 0;
            for (var sessionId : repository.findWaitingSessionIds()) {
                total += repository.countWaitingUsers(sessionId);
            }
            waitingCount = total;
            sampleSuccess = 1;
        } catch (RuntimeException exception) {
            waitingCount = Double.NaN;
            sampleSuccess = 0;
            log.warn("Queue waiting count sampling failed", exception);
        }
    }

    @PreDestroy
    public void stop() {
        sampler.shutdown();
    }
}
