package com.tikitaka.platform.venue.infrastructure;

import com.tikitaka.platform.venue.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
