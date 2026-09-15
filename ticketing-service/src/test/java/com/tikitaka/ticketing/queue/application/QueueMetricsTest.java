package com.tikitaka.ticketing.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueueMetricsTest {
    @Test
    void waitingSampleIsUnknownOnFailureAndRecoversWithoutKeepingStaleCount() {
        var repository = mock(QueueRepository.class);
        var registry = new SimpleMeterRegistry();
        var metrics = new QueueWaitingMetrics(repository, registry);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findWaitingSessionIds()).thenReturn(Set.of(first, second));
        when(repository.countWaitingUsers(first)).thenReturn(3L);
        when(repository.countWaitingUsers(second)).thenReturn(7L);
        assertThat(registry.get("queue.waiting.count").gauge().value()).isNaN();
        metrics.sample();
        assertThat(registry.get("queue.waiting.count").gauge().value()).isEqualTo(10);
        when(repository.findWaitingSessionIds()).thenThrow(new IllegalStateException("unavailable"));
        metrics.sample();
        assertThat(registry.get("queue.waiting.count").gauge().value()).isNaN();
        assertThat(registry.get("queue.waiting.sample.success").gauge().value()).isZero();
        org.mockito.Mockito.doReturn(Set.of()).when(repository).findWaitingSessionIds();
        metrics.sample();
        assertThat(registry.get("queue.waiting.count").gauge().value()).isZero();
        assertThat(registry.get("queue.waiting.sample.success").gauge().value()).isEqualTo(1);
    }

    @Test
    void schedulerRecordsFailedExecutionAndPreservesException() {
        var registry = new SimpleMeterRegistry();
        var metrics = new QueueMetrics(registry);
        assertThatThrownBy(() -> metrics.recordScheduler(() -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get("queue.scheduler.duration").timer().count()).isEqualTo(1);
    }
}
