package com.tikitaka.ticketing.seat.infrastructure.repository;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SeatHoldJpaRepository extends JpaRepository<SeatHold, UUID> {

    Optional<SeatHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
