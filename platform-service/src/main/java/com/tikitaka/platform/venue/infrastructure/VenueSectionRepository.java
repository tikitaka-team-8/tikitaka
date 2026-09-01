package com.tikitaka.platform.venue.infrastructure;

import com.tikitaka.platform.venue.domain.VenueSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueSectionRepository extends JpaRepository<VenueSection,UUID> {
}
