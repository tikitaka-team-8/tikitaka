package com.tikitaka.ticketing.seat.infrastructure.adapter;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.repository.SeatHoldRepository;
import com.tikitaka.ticketing.seat.infrastructure.repository.SeatHoldJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SeatHoldRepositoryAdapter implements SeatHoldRepository {

    private final SeatHoldJpaRepository jpaRepository;

    @Override
    public Optional<SeatHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return jpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Override
    public SeatHold save(SeatHold seatHold) {
        return jpaRepository.saveAndFlush(seatHold);
    }
}
