package com.tikitaka.platform.organizer.infrastructure;

import com.tikitaka.platform.organizer.domain.Organizer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizerRepository extends JpaRepository<Organizer, UUID> {

}
