package com.tikitaka.ticketing.queue.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Fixed stage names only: no user, session or token labels. */
@Component
@ConditionalOnProperty(name = "queue.diagnostics.registration", havingValue = "true")
public class QueueRegistrationMetrics {
    private final Map<String, Timer> timers = new HashMap<>();

    public QueueRegistrationMetrics(MeterRegistry registry) {
        for (String stage : new String[]{"total", "platform", "redis_lookup", "redis_create",
                "redis_registry", "redis_reread"}) {
            timers.put(stage, Timer.builder("queue.registration.stage")
                    .tag("stage", stage)
                    .description("Queue registration stage wall time, including failed calls")
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    public <T> T measure(String stage, Supplier<T> action) {
        return timers.get(stage).record(action);
    }
}
