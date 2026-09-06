package com.tikitaka.ticketing.queue.application;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionSchedulerTest {
    @Mock
    private QueueAdmissionService queueAdmissionService;

    @Test
    void 대기_입장과_입장_권한_만료를_함께_처리한다() {
        QueueAdmissionScheduler scheduler = new QueueAdmissionScheduler(queueAdmissionService);

        scheduler.processQueueAdmissions();

        verify(queueAdmissionService).admitWaitingUsers();
        verify(queueAdmissionService).expireAdmittedUsers();
    }
}
