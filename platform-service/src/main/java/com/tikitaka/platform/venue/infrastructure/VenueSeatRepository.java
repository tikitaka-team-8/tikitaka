package com.tikitaka.platform.venue.infrastructure;

import com.tikitaka.platform.venue.domain.VenueSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VenueSeatRepository extends JpaRepository<VenueSeat, UUID> {

  @Query("""
    SELECT seat
    FROM VenueSeat seat
    JOIN FETCH seat.venueSection section
    WHERE section.venue.id = :venueId
      AND section.active = true
      AND seat.active = true
    ORDER BY section.displayOrder, seat.rowLabel, seat.seatNumber
    """)
  List<VenueSeat> findAllActiveByVenueId(
      @Param("venueId") UUID venueId);
}
