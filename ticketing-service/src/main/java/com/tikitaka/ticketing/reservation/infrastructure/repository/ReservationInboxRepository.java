package com.tikitaka.ticketing.reservation.infrastructure.repository;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReservationInboxRepository extends JpaRepository<ReservationInbox, UUID> {
}
