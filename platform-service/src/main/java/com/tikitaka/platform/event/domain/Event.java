package com.tikitaka.platform.event.domain;

import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.venue.domain.Venue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;


  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "organizer_id",
      nullable = false,
      updatable = false
  )
  private Organizer organizer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "venue_id", nullable = false)
  private Venue venue;

  @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
  private List<EventSession> sessions = new ArrayList<>();

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description")
  private String description;

  @Column(name = "running_time_minutes", nullable = false)
  private Integer runningTimeMinutes;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private EventStatus status;

  private Event(
      Organizer organizer,
      Venue venue,
      String title,
      String description,
      Integer runningTimeMinutes
  ) {

    this.organizer = organizer;
    this.venue = venue;
    this.title = title;
    this.description = description;
    this.runningTimeMinutes = runningTimeMinutes;
    this.status = EventStatus.DRAFT;
  }

  public static Event create(
    Organizer organizer,
    Venue venue,
    String title,
    String description,
    Integer runningTimeMinutes
  ) {
    return new Event(
        organizer,
        venue,
        title,
        description,
        runningTimeMinutes
    );
  }

  // 수정
  public void update(
      Venue venue,
      String title,
      String description,
      Integer runningTimeMinutes
  ) {
    // Draft상태에서만 수정 가능
    validateModifiableStatus();

    if (venue != null) {
      this.venue = venue;
    }

    if (title != null) {
      this.title = title;
    }

    if (description != null) {
      this.description = description;
    }

    if (runningTimeMinutes != null) {
      this.runningTimeMinutes = runningTimeMinutes;
    }
  }

  // 공연 공개
  public void publish() {

    if (this.status == EventStatus.UPCOMING) {
      return;
    }

    // DRAFT 상태에서만 공연 공개 가능
    validatePublishableStatus();
    this.status = EventStatus.UPCOMING;
  }

  public boolean isPubliclyVisible() {
    return status.isPubliclyVisible();
  }

  private void validateModifiableStatus() {
    if (this.status != EventStatus.DRAFT) {
      throw new BusinessException(EventErrorCode.EVENT_NOT_MODIFIABLE);
    }
  }

  private void validatePublishableStatus() {
    if (this.status != EventStatus.DRAFT) {
      throw new BusinessException(EventErrorCode.INVALID_EVENT_STATUS);
    }
  }
}
