package com.tikitaka.platform.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_session_section_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionSectionPrice {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY,  optional = false)
  @JoinColumn(name = "event_session_id", nullable = false)
  private EventSession eventSession;

  @Column(name = "venue_section_id", nullable = false)
  private UUID venueSectionId;

  @Column(name = "seat_grade", nullable = false, length = 30)
  private String seatGrade;

  @Column(name = "price_amount", nullable = false)
  private long priceAmount;

  @Column(name = "sales_enabled", nullable = false)
  private boolean salesEnabled = true;

  private SessionSectionPrice(
      EventSession eventSession,
      UUID venueSectionId,
      String seatGrade,
      long priceAmount
  ) {

    // TODO: 검증 필요
    this.eventSession = eventSession;
    this.venueSectionId = venueSectionId;
    this.seatGrade = seatGrade;
    this.priceAmount = priceAmount;
    this.salesEnabled = true;
  }

  public static SessionSectionPrice create(
      EventSession eventSession,
      UUID venueSectionId,
      String seatGrade,
      long priceAmount
  ) {
    return new SessionSectionPrice(
        eventSession,
        venueSectionId,
        seatGrade,
        priceAmount
    );
  }
}
