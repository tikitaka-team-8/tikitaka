package com.tikitaka.platform.event.infrastructure;

import com.tikitaka.platform.event.domain.SessionSectionPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionSectionPriceRepository extends JpaRepository<SessionSectionPrice, UUID> {

  void deleteAllByEventSessionId(UUID sessionId);
}
