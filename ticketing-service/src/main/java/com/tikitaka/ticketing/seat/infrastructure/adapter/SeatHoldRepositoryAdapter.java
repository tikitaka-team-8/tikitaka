package com.tikitaka.ticketing.seat.infrastructure.adapter;

import com.tikitaka.ticketing.seat.domain.repository.SeatHoldRepository;
import com.tikitaka.ticketing.seat.infrastructure.repository.SeatHoldJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SeatHoldRepositoryAdapter implements SeatHoldRepository {

    private final SeatHoldJpaRepository jpaRepository;
}
