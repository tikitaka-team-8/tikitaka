package com.tikitaka.ticketing.reservation.infrastructure.repository;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationOutbox;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationOutboxRepository extends JpaRepository<ReservationOutbox, UUID> {

    List<ReservationOutbox> findAllByStatusOrderByCreatedAtAsc(ReservationOutboxStatus status);
}
