package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.reservation.domain.model.ReservationCreationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.SeatHoldValidationInfo;
import com.tikitaka.ticketing.reservation.domain.port.SeatHoldQueryPort;
import com.tikitaka.ticketing.reservation.infrastructure.repository.ReservationRepository;
import com.tikitaka.ticketing.seat.infrastructure.repository.SeatHoldJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SeatHoldQueryAdapter implements SeatHoldQueryPort {

    private final SeatHoldJpaRepository seatHoldJpaRepository;
    private final ReservationRepository reservationRepository;

    public SeatHoldQueryAdapter(SeatHoldJpaRepository seatHoldJpaRepository, ReservationRepository reservationRepository) {
        this.seatHoldJpaRepository = seatHoldJpaRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public List<SeatHoldValidationInfo> findAllByIds(List<UUID> seatHoldIds) {

        return seatHoldJpaRepository.findAllById(seatHoldIds).stream()
                .map(seatHold -> new SeatHoldValidationInfo(
                        seatHold.getSeatHoldId(), seatHold.getUserId(), seatHold.getHoldStatus(), seatHold.getExpiresAt()
                )).toList();
    }

    @Override
    public List<ReservationCreationSeatInfo> findCreationInfosBySeatHoldIds(List<UUID> seatHoldIds) {
        return reservationRepository.findCreationInfosBySeatHoldIds(seatHoldIds);
    }
}
