package com.tikitaka.platform.venue.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_venue_seat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueSeat {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "section_id", nullable = false, updatable = false)
  private VenueSection venueSection;

  @Column(name = "row_label", nullable = false, length = 20)
  private String rowLabel;

  @Column(name = "seat_number", nullable = false, length = 20)
  private String seatNumber;

  @Column(name = "active", nullable = false)
  private boolean active = true;
}
