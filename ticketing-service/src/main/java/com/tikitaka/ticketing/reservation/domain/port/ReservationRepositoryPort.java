package com.tikitaka.ticketing.reservation.domain.port;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepositoryPort {

    Optional<Reservation> findById(UUID reservationId);

    List<ReservationSeatDetail> findSeatDetailsByReservationId(UUID reservationId);

    Page<Reservation> searchReservations(Long ownerUserId, String eventTitle, ReservationStatus reservationStatus, Pageable pageable);
}
