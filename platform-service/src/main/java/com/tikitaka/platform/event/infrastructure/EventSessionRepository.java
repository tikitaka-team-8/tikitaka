package com.tikitaka.platform.event.infrastructure;

import com.tikitaka.platform.event.domain.EventSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventSessionRepository extends JpaRepository<EventSession,UUID> {
}
