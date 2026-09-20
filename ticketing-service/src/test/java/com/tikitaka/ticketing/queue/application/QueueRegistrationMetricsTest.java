package com.tikitaka.ticketing.queue.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueueRegistrationMetricsTest {
    @Test
    void preservesResultsAndExceptionsAndRecordsFailedCalls() {
        var registry = new SimpleMeterRegistry();
        var metrics = new QueueRegistrationMetrics(registry);
        Object value = new Object();
        assertSame(value, metrics.measure("redis_lookup", () -> value));
        var failure = new IllegalStateException("downstream unavailable");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> metrics.measure("total", () -> metrics.measure("platform", () -> { throw failure; }))));
        for (String stage : new String[]{"redis_lookup", "platform", "total"}) {
            assertEquals(1, registry.get("queue.registration.stage").tag("stage", stage).timer().count());
        }
    }
}
