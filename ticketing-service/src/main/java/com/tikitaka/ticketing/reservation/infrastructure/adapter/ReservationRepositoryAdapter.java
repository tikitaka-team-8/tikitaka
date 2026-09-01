package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatDetail;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.infrastructure.repository.ReservationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepositoryAdapter implements ReservationRepositoryPort {
    private final ReservationRepository reservationRepository;

    public ReservationRepositoryAdapter(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public Optional<Reservation> findById(UUID reservationId) {
        return reservationRepository.findById(reservationId);
    }

    @Override
    public List<ReservationSeatDetail> findSeatDetailsByReservationId(UUID reservationId) {
        return reservationRepository.findSeatDetailsByReservationId(reservationId);
    }
}
