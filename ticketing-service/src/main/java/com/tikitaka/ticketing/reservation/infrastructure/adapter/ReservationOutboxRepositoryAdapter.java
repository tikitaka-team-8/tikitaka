package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationOutbox;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxStatus;
import com.tikitaka.ticketing.reservation.domain.port.ReservationOutboxRepositoryPort;
import com.tikitaka.ticketing.reservation.infrastructure.repository.ReservationOutboxRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReservationOutboxRepositoryAdapter implements ReservationOutboxRepositoryPort {

    private final ReservationOutboxRepository reservationOutboxRepository;

    public ReservationOutboxRepositoryAdapter(ReservationOutboxRepository reservationOutboxRepository) {
        this.reservationOutboxRepository = reservationOutboxRepository;
    }

    @Override
    public ReservationOutbox save(ReservationOutbox reservationOutbox) {
        return reservationOutboxRepository.save(reservationOutbox);
    }

    @Override
    public List<ReservationOutbox> findPendingOutboxes() {
        return reservationOutboxRepository.findAllByStatusOrderByCreatedAtAsc(ReservationOutboxStatus.PENDING);
    }
}
