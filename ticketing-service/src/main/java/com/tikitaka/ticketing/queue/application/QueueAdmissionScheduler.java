package com.tikitaka.ticketing.queue.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueAdmissionScheduler {
    private final QueueAdmissionService queueAdmissionService;

    public QueueAdmissionScheduler(QueueAdmissionService queueAdmissionService) {
        this.queueAdmissionService = queueAdmissionService;
    }

    @Scheduled(fixedDelayString = "${queue.admission-interval:PT1S}")
    public void processQueueAdmissions() {
        queueAdmissionService.admitWaitingUsers();
        queueAdmissionService.expireAdmittedUsers();
    }
}
