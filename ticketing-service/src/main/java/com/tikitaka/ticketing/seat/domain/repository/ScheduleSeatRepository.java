package com.tikitaka.ticketing.seat.domain.repository;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleSeatRepository {

    List<ScheduleSeat> findSeats(UUID eventSessionId, String section, String grade);

    Optional<ScheduleSeat> findSeatDetail(UUID eventSessionId, UUID scheduleSeatId);

    Optional<ScheduleSeat> findByIdForUpdate(UUID eventSessionId, UUID scheduleSeatId);

    Optional<ScheduleSeat> findByIdForUpdate(UUID eventSessionId);

}
