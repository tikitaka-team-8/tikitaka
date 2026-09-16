package com.tikitaka.ticketing.seat.domain.repository;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleSeatRepository {

    Page<ScheduleSeat> findSeats(UUID eventSessionId, String section, String grade, Pageable pageable);

    Optional<ScheduleSeat> findSeatDetail(UUID eventSessionId, UUID scheduleSeatId);

    Optional<ScheduleSeat> findByIdForUpdate(UUID eventSessionId, UUID scheduleSeatId);

    Optional<ScheduleSeat> findByIdForUpdate(UUID eventSessionId);

    List<UUID> findExistingVenueSeatIds(UUID eventSessionId, List<UUID> venueSeatIds);

    List<ScheduleSeat> saveAll(List<ScheduleSeat> scheduleSeats);

}
