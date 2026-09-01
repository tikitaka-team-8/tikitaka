package com.tikitaka.ticketing.seat.infrastructure.adapter;

import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.infrastructure.repository.ScheduleSeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScheduleSeatRepositoryAdapter implements ScheduleSeatRepository {

    private final ScheduleSeatJpaRepository jpaRepository;
}
