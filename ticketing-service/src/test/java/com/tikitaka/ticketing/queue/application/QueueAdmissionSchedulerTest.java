package com.tikitaka.ticketing.queue.application;

import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionSchedulerTest {
    @Mock
    private QueueAdmissionService queueAdmissionService;

    @Test
    void heartbeat_만료_정리_후_대기_입장과_입장_권한_만료를_처리한다() {
        QueueAdmissionScheduler scheduler = new QueueAdmissionScheduler(queueAdmissionService);

        scheduler.processQueueAdmissions();

        InOrder inOrder = inOrder(queueAdmissionService);
        inOrder.verify(queueAdmissionService).expireInactiveWaitingUsers();
        inOrder.verify(queueAdmissionService).admitWaitingUsers();
        inOrder.verify(queueAdmissionService).expireAdmittedUsers();
    }
}
