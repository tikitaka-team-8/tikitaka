package com.tikitaka.ticketing.seat.infrastructure.repository;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldJpaRepository extends JpaRepository<SeatHold, UUID> {

    Optional<SeatHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    List<SeatHold> findByHoldStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            HoldStatus holdStatus,
            Instant expiresAt,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
            @QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "3000"
            )
    )
    @Query("""
        SELECT sh
        FROM SeatHold sh
        WHERE sh.seatHoldId = :seatHoldId
    """)
    Optional<SeatHold> findByIdForUpdate(@Param("seatHoldId") UUID seatHoldId);
}
