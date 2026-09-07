package com.tikitaka.ticketing.reservation.infrastructure.repository;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.ReservationCreationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("""
            SELECT new com.tikitaka.ticketing.reservation.domain.model.ReservationCreationSeatInfo(
                    sh.seatHoldId, sh.scheduleSeatId, sh.userId, sh.holdStatus, sh.expiresAt, ss.eventSessionId, ss.price
            )
            FROM SeatHold sh
            JOIN ScheduleSeat ss ON ss.scheduleSeatId = sh.scheduleSeatId
            WHERE sh.seatHoldId IN :seatHoldIds
            """)
    List<ReservationCreationSeatInfo> findCreationInfosBySeatHoldIds(List<UUID> seatHoldIds);

    @Query("""
            SELECT rs.seatHoldId
            FROM ReservationSeat rs
            WHERE rs.seatHoldId IN :seatHoldIds
            """)
    List<UUID> findUsedSeatHoldIds(List<UUID> seatHoldIds);

    @Query("""
            SELECT new com.tikitaka.ticketing.reservation.domain.model.ReservationSeatInfo(
                    ss.scheduleSeatId, ss.section, ss.rowLabel, ss.seatNumber, ss.seatGrade, rs.price
            )
            FROM ReservationSeat rs
            JOIN ScheduleSeat ss ON ss.scheduleSeatId = rs.scheduleSeatId
            WHERE rs.reservation.reservationId = :reservationId
            ORDER BY rs.createdAt ASC, rs.reservationSeatId ASC
            """)
    List<ReservationSeatInfo> findSeatDetailsByReservationId(@Param("reservationId") UUID reservationId);

    @Query("""
            SELECT r
            FROM Reservation r
            WHERE (:ownerUserId IS NULL OR r.userId = :ownerUserId)
              AND (:eventTitle IS NULL OR LOWER(r.eventTitle) LIKE LOWER(CONCAT('%', :eventTitle, '%')))
              AND (:reservationStatus IS NULL OR r.reservationStatus = :reservationStatus)
            """)
    Page<Reservation> searchReservations(Long ownerUserId, String eventTitle, ReservationStatus reservationStatus, Pageable pageable);
}
