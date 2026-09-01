package com.tikitaka.ticketing.seat.infrastructure.repository;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScheduleSeatJpaRepository extends JpaRepository<ScheduleSeat, UUID> {

}