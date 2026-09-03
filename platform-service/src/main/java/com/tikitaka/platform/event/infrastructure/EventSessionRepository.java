package com.tikitaka.platform.event.infrastructure;

import com.tikitaka.platform.event.domain.EventSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventSessionRepository extends JpaRepository<EventSession,UUID> {


  // sessionSectionPrice와 venue 같이 가져옴
  @Query("""
        SELECT DISTINCT es
        FROM EventSession es
        LEFT JOIN FETCH es.sectionPrices sp
        LEFT JOIN FETCH sp.venueSection vs
        WHERE es.id = :sessionId
          AND es.event.id = :eventId
  """)
  Optional<EventSession> findDetailByIdAndEventId(
      @Param("sessionId") UUID sessionId,
      @Param("eventId") UUID eventId
  );
}
