package com.tikitaka.platform.event.infrastructure;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends
    JpaRepository<Event, UUID>, EventRepositoryCustom {


  @Query("""
      SELECT DISTINCT e
      FROM Event e
      JOIN FETCH e.venue v
      LEFT JOIN FETCH e.sessions s
      WHERE e.id = :eventId
        AND e.status IN :statuses
      """)
  Optional<Event> findPublicEventDetail(
      @Param("eventId") UUID eventId,
      @Param("statuses") Collection<EventStatus> statuses
  );
}
