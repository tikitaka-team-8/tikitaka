package com.tikitaka.ticketing.queue.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueAdmissionScheduler {
    private final QueueAdmissionService queueAdmissionService;
    private final QueueMetrics metrics;

    public QueueAdmissionScheduler(QueueAdmissionService queueAdmissionService, QueueMetrics metrics) {
        this.queueAdmissionService = queueAdmissionService;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${queue.admission-interval:PT1S}")
    public void processQueueAdmissions() {
        metrics.recordScheduler(() -> {
            queueAdmissionService.expireInactiveWaitingUsers();
            queueAdmissionService.admitWaitingUsers();
            queueAdmissionService.expireAdmittedUsers();
        });
    }
}
