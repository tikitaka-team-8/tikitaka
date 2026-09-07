package com.tikitaka.ticketing.seat.domain.repository;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldRepository {

    Optional<SeatHold> findById(UUID seatHoldId);

    Optional<SeatHold> findByIdForUpdate(UUID seatHoldId);

    Optional<SeatHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    SeatHold save(SeatHold seatHold);

    List<SeatHold> findExpiredHolds(Instant now, int limit);
}
