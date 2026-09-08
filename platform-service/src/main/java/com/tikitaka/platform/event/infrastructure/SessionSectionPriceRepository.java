package com.tikitaka.platform.event.infrastructure;

import com.tikitaka.platform.event.domain.SessionSectionPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SessionSectionPriceRepository extends JpaRepository<SessionSectionPrice, UUID> {


  @Modifying
  @Query("""
    DELETE FROM SessionSectionPrice sp
        WHERE sp.eventSession.id = :sessionId
    """)
  void deleteAllByEventSessionId(
      @Param("sessionId") UUID sessionId);

  @Query("""
    SELECT price
    FROM SessionSectionPrice price
    JOIN FETCH price.venueSection
    WHERE price.eventSession.id = :sessionId
    """)
  List<SessionSectionPrice> findAllByEventSessionId(
      @Param("sessionId") UUID sessionId);
}
