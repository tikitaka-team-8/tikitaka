package com.tikitaka.ticketing.seat.infrastructure.repository;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.projection.ScheduleSeatSummary;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleSeatJpaRepository extends JpaRepository<ScheduleSeat, UUID> {

    @Query(
        value = """
            SELECT s
            FROM ScheduleSeat s
            WHERE s.eventSessionId = :eventSessionId
              AND (:section IS NULL OR s.section = :section)
              AND (:grade IS NULL OR s.seatGrade = :grade)
            ORDER BY s.section, s.rowLabel, s.seatNumber
            """,
        countQuery = """
            SELECT COUNT(s)
            FROM ScheduleSeat s
            WHERE s.eventSessionId = :eventSessionId
              AND (:section IS NULL OR s.section = :section)
              AND (:grade IS NULL OR s.seatGrade = :grade)
            """
    )
    Page<ScheduleSeat> findSeats(
            @Param("eventSessionId") UUID eventSessionId,
            @Param("section") String section,
            @Param("grade") String grade,
            Pageable pageable
    );


    // "필요한 필드만 SELECT" 실험용: ScheduleSeat 엔티티 전체(15개 컬럼) 대신
    // 응답에 실제 필요한 7개 컬럼만 JPQL 생성자 표현식으로 바로 채운다.
    @Query(
        value = """
            SELECT new com.tikitaka.ticketing.seat.domain.projection.ScheduleSeatSummary(
                s.scheduleSeatId, s.section, s.rowLabel, s.seatNumber, s.seatGrade, s.price, s.seatStatus
            )
            FROM ScheduleSeat s
            WHERE s.eventSessionId = :eventSessionId
              AND (:section IS NULL OR s.section = :section)
              AND (:grade IS NULL OR s.seatGrade = :grade)
            ORDER BY s.section, s.rowLabel, s.seatNumber
            """,
        countQuery = """
            SELECT COUNT(s)
            FROM ScheduleSeat s
            WHERE s.eventSessionId = :eventSessionId
              AND (:section IS NULL OR s.section = :section)
              AND (:grade IS NULL OR s.seatGrade = :grade)
            """
    )
    Page<ScheduleSeatSummary> findSeatSummaries(
            @Param("eventSessionId") UUID eventSessionId,
            @Param("section") String section,
            @Param("grade") String grade,
            Pageable pageable
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
            @QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "3000"
            )
    )
    @Query("""
        SELECT s
        FROM ScheduleSeat s
        WHERE s.eventSessionId = :eventSessionId
          AND s.scheduleSeatId = :scheduleSeatId
    """)
    Optional<ScheduleSeat> findByIdForUpdate(
            @Param("eventSessionId") UUID eventSessionId,
            @Param("scheduleSeatId") UUID scheduleSeatId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
            @QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "3000"
            )
    )
    @Query("""
        SELECT s
        FROM ScheduleSeat s
        WHERE s.scheduleSeatId = :scheduleSeatId
    """)
    Optional<ScheduleSeat> findByIdForUpdate(
            @Param("scheduleSeatId") UUID scheduleSeatId
    );

    @Query("""
        SELECT s.venueSeatId
        FROM ScheduleSeat s
        WHERE s.eventSessionId = :eventSessionId
          AND s.venueSeatId IN :venueSeatIds
    """)
    List<UUID> findExistingVenueSeatIds(
            @Param("eventSessionId") UUID eventSessionId,
            @Param("venueSeatIds") List<UUID> venueSeatIds
    );
}
