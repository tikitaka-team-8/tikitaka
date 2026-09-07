package com.tikitaka.ticketing.reservation.domain.port;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationInbox;

import java.util.UUID;

public interface ReservationInboxRepositoryPort {

    boolean existsByEventId(UUID eventId);

    ReservationInbox save(ReservationInbox reservationInbox);
}
