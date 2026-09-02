package com.tikitaka.ticketing.seat.infrastructure.adapter;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.infrastructure.repository.ScheduleSeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ScheduleSeatRepositoryAdapter implements ScheduleSeatRepository {

    private final ScheduleSeatJpaRepository jpaRepository;

    @Override
    public List<ScheduleSeat> findSeats(UUID eventSessionId, String section, String grade) {
        return jpaRepository.findSeats(
                eventSessionId,
                section,
                grade
        );
    }

    @Override
    public Optional<ScheduleSeat> findSeatDetail(UUID eventSessionId, UUID scheduleSeatId) {
        return jpaRepository.findSeatDetail(eventSessionId,scheduleSeatId);
    }
}
