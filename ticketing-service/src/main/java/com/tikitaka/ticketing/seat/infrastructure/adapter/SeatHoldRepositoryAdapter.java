package com.tikitaka.ticketing.seat.infrastructure.adapter;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.repository.SeatHoldRepository;
import com.tikitaka.ticketing.seat.infrastructure.repository.SeatHoldJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SeatHoldRepositoryAdapter implements SeatHoldRepository {

    private final SeatHoldJpaRepository jpaRepository;

    @Override
    public Optional<SeatHold> findById(UUID seatHoldId) {
        return jpaRepository.findById(seatHoldId);
    }

    @Override
    public Optional<SeatHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return jpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Override
    public SeatHold save(SeatHold seatHold) {
        return jpaRepository.saveAndFlush(seatHold);
    }
}
