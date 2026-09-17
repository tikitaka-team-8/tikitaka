package com.tikitaka.ticketing.seat.domain.repository;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.projection.ScheduleSeatSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleSeatRepository {

    Page<ScheduleSeat> findSeats(UUID eventSessionId, String section, String grade, Pageable pageable);

    // "필요한 필드만 SELECT" 실험용 프로젝션 조회
    Page<ScheduleSeatSummary> findSeatSummaries(UUID eventSessionId, String section, String grade, Pageable pageable);

    Optional<ScheduleSeat> findSeatDetail(UUID eventSessionId, UUID scheduleSeatId);

    Optional<ScheduleSeat> findByIdForUpdate(UUID eventSessionId, UUID scheduleSeatId);

    Optional<ScheduleSeat> findByIdForUpdate(UUID eventSessionId);

    List<UUID> findExistingVenueSeatIds(UUID eventSessionId, List<UUID> venueSeatIds);

    List<ScheduleSeat> saveAll(List<ScheduleSeat> scheduleSeats);

}
