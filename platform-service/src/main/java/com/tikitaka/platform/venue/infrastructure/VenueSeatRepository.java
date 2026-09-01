package com.tikitaka.platform.venue.infrastructure;

import com.tikitaka.platform.venue.domain.VenueSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueSeatRepository extends JpaRepository<VenueSeat, UUID> {
}
