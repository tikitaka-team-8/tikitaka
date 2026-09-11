package com.tikitaka.platform.event.infrastructure;

import com.tikitaka.platform.event.domain.EventSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

  @Query("""
      SELECT es
      FROM EventSession es
      JOIN FETCH es.event
      WHERE es.id = :eventSessionId
      
  """)
  Optional<EventSession> findByIdWithEvent(
      @Param("eventSessionId") UUID eventSessionId
  );

  @Query("""
      select coalesce(max(es.sessionNumber), 0)
      FROM EventSession  es
      WHERE es.event.id = :eventId
  """)
  int findMaxSessionNumber(
      @Param("eventId") UUID eventId);

  Optional<EventSession> findByIdAndEventId(UUID sessionId, UUID eventId);

  @Query("""
    SELECT DISTINCT session
    FROM EventSession session
    LEFT JOIN FETCH session.sectionPrices price
    LEFT JOIN FETCH price.venueSection section
    LEFT JOIN FETCH section.venue
    WHERE session.event.id = :eventId
    ORDER BY session.sessionNumber
  """)
  List<EventSession> findAllForPublication(
      @Param("eventId") UUID eventId);

  List<EventSession> findAllByEventId(UUID eventId);
}
