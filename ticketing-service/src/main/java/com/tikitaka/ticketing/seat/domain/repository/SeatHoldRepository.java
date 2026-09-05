package com.tikitaka.ticketing.seat.domain.repository;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;

import java.util.Optional;
import java.util.UUID;

public interface SeatHoldRepository {

    Optional<SeatHold> findById(UUID seatHoldId);

    Optional<SeatHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    SeatHold save(SeatHold seatHold);
}
