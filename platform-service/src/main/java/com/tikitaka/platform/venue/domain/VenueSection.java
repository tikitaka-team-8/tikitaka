package com.tikitaka.platform.venue.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_venue_section")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueSection {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "venue_id", nullable = false, updatable = false)
  private Venue venue;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "floor_label", length = 20)
  private String floorLabel;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "active", nullable = false)
  private boolean active = true;
}
