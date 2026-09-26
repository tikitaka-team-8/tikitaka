package com.tikitaka.ticketing.queue.application;

import java.util.UUID;

public interface QueueReservationFlow {

    void bindReservationFlow(UUID eventSessionId, long userId, UUID reservationId);

    void complete(UUID eventSessionId, long userId, UUID reservationId);
}
