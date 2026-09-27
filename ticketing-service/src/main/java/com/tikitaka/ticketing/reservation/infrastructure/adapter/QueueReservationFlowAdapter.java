package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.queue.application.QueueReservationFlow;
import com.tikitaka.ticketing.reservation.domain.port.ReservationQueueFlowPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QueueReservationFlowAdapter implements ReservationQueueFlowPort {
    private final QueueReservationFlow queueReservationFlow;

    public QueueReservationFlowAdapter(QueueReservationFlow queueReservationFlow) {
        this.queueReservationFlow = queueReservationFlow;
    }

    @Override
    public void bindReservationFlow(UUID eventSessionId, long userId, UUID reservationId) {
        queueReservationFlow.bindReservationFlow(eventSessionId, userId, reservationId);
    }

    @Override
    public void completeReservationFlow(UUID eventSessionId, long userId, UUID reservationId) {
        queueReservationFlow.complete(eventSessionId, userId, reservationId);
    }
}
