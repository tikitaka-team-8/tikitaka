package com.tikitaka.ticketing.queue.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class QueueMetrics {
    private final Counter admissions;
    private final Counter heartbeatExpirations;
    private final Timer schedulerDuration;

    public QueueMetrics(MeterRegistry registry) {
        admissions = Counter.builder("queue.admission")
                .description("Successful WAITING to ADMITTED transitions in this process")
                .register(registry);
        heartbeatExpirations = Counter.builder("queue.heartbeat.expired")
                .description("WAITING entries actually removed after heartbeat timeout in this process")
                .register(registry);
        schedulerDuration = Timer.builder("queue.scheduler.duration")
                .description("Full scheduler execution, including heartbeat and token cleanup; excludes fixed delay")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void admitted() {
        admissions.increment();
    }

    public void heartbeatExpired() {
        heartbeatExpirations.increment();
    }

    public void recordScheduler(Runnable action) {
        schedulerDuration.record(action);
    }
}
