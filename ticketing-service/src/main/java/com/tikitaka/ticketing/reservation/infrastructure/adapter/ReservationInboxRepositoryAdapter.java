package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationInbox;
import com.tikitaka.ticketing.reservation.domain.port.ReservationInboxRepositoryPort;
import com.tikitaka.ticketing.reservation.infrastructure.repository.ReservationInboxRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ReservationInboxRepositoryAdapter implements ReservationInboxRepositoryPort {

    private final ReservationInboxRepository reservationInboxRepository;

    public ReservationInboxRepositoryAdapter(ReservationInboxRepository reservationInboxRepository) {
        this.reservationInboxRepository = reservationInboxRepository;
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return reservationInboxRepository.existsById(eventId);
    }

    @Override
    public ReservationInbox save(ReservationInbox reservationInbox) {
        return reservationInboxRepository.save(reservationInbox);
    }
}
