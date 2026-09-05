package com.tikitaka.ticketing.seat.domain.repository;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;

import java.util.Optional;

public interface SeatHoldRepository {

    Optional<SeatHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    SeatHold save(SeatHold seatHold);
}
