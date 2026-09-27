package com.tikitaka.ticketing.reservation.domain.port;

import java.util.UUID;

public interface ReservationQueueFlowPort {

    void bindReservationFlow(UUID eventSessionId, long userId, UUID reservationId);

    void completeReservationFlow(UUID eventSessionId, long userId, UUID reservationId);
}
