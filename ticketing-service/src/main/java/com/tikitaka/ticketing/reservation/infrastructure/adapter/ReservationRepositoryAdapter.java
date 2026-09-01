package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.infrastructure.repository.ReservationRepository;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationRepositoryAdapter implements ReservationRepositoryPort {
    private ReservationRepository reservationRepository;

    public ReservationRepositoryAdapter(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }
}
