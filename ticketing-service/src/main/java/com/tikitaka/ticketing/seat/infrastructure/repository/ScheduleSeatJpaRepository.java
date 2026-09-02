package com.tikitaka.ticketing.seat.infrastructure.repository;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleSeatJpaRepository extends JpaRepository<ScheduleSeat, UUID> {

    @Query("""
        SELECT s
        FROM ScheduleSeat s
        WHERE s.eventSessionId = :eventSessionId
          AND (:section IS NULL OR s.section = :section)
          AND (:grade IS NULL OR s.seatGrade = :grade)
        ORDER BY s.section, s.rowLabel, s.seatNumber
        """)
    List<ScheduleSeat> findSeats(
            @Param("eventSessionId") UUID eventSessionId,
            @Param("section") String section,
            @Param("grade") String grade
    );


    @Query("""
        SELECT s
        FROM ScheduleSeat s
        WHERE s.eventSessionId = :eventSessionId
          AND s.scheduleSeatId = :scheduleSeatId
    """)
    Optional<ScheduleSeat> findSeatDetail(
            @Param("eventSessionId") UUID eventSessionId,
            @Param("scheduleSeatId") UUID scheduleSeatId
    );
}