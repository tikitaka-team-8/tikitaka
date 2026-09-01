package com.tikitaka.ticketing.seat.infrastructure.repository;

import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeatHoldJpaRepository extends JpaRepository<SeatHold, UUID> {
}
