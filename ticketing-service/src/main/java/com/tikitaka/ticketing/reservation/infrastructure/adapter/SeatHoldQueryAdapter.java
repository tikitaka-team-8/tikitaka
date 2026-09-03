package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.reservation.domain.model.SeatHoldValidationInfo;
import com.tikitaka.ticketing.reservation.domain.port.SeatHoldQueryPort;
import com.tikitaka.ticketing.seat.infrastructure.repository.SeatHoldJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SeatHoldQueryAdapter implements SeatHoldQueryPort {

    private final SeatHoldJpaRepository seatHoldJpaRepository;

    public SeatHoldQueryAdapter(SeatHoldJpaRepository seatHoldJpaRepository) {
        this.seatHoldJpaRepository = seatHoldJpaRepository;
    }

    @Override
    public List<SeatHoldValidationInfo> findAllByIds(List<UUID> seatHoldIds) {

        return seatHoldJpaRepository.findAllById(seatHoldIds).stream()
                .map(seatHold -> new SeatHoldValidationInfo(
                        seatHold.getSeatHoldId(), seatHold.getUserId(), seatHold.getHoldStatus(), seatHold.getExpiresAt()
                )).toList();
    }
}
