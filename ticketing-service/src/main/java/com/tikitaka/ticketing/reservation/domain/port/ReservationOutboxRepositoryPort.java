package com.tikitaka.ticketing.reservation.domain.port;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationOutbox;

import java.util.List;

public interface ReservationOutboxRepositoryPort {

    ReservationOutbox save(ReservationOutbox reservationOutbox);

    List<ReservationOutbox> findPendingOutboxes();
}
