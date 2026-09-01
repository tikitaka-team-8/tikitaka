package com.tikitaka.platform.event.domain;

import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_event_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSession {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "event_id",
      nullable = false,
      updatable = false
  )
  private Event event;


  @OneToMany(
      mappedBy = "eventSession",
      cascade = CascadeType.ALL,
      orphanRemoval = true
  )
  private List<SessionSectionPrice> sectionPrices = new ArrayList<>();

  @Column(name = "session_number", nullable = false)
  private Integer sessionNumber;

  @Column(name = "performance_start_at", nullable = false)
  private OffsetDateTime performanceStartAt;

  @Column(name = "performance_end_at", nullable = false)
  private OffsetDateTime performanceEndAt;

  @Column(name = "sales_open_at", nullable = false)
  private OffsetDateTime salesOpenAt;

  @Column(name = "sales_close_at", nullable = false)
  private OffsetDateTime salesCloseAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private EventSessionStatus status;

  private EventSession(
      Event event,
      Integer sessionNumber,
      OffsetDateTime performanceStartAt,
      OffsetDateTime performanceEndAt,
      OffsetDateTime salesOpenAt,
      OffsetDateTime salesCloseAt
  ) {

    // 공연 검증
    validateCreateEvent(event);

    // 회차 시간 검증
    validateSchedule(
        sessionNumber,
        performanceStartAt,
        performanceEndAt,
        salesOpenAt,
        salesCloseAt
    );

    this.event = event;
    this.sessionNumber = sessionNumber;
    this.performanceStartAt = performanceStartAt;
    this.performanceEndAt = performanceEndAt;
    this.salesOpenAt = salesOpenAt;
    this.salesCloseAt = salesCloseAt;
    this.status = EventSessionStatus.SCHEDULED;
  }

  public static EventSession create(
      Event event,
      Integer sessionNumber,
      OffsetDateTime performanceStartAt,
      OffsetDateTime performanceEndAt,
      OffsetDateTime salesOpenAt,
      OffsetDateTime salesCloseAt
  ) {
    return new EventSession(
        event,
        sessionNumber,
        performanceStartAt,
        performanceEndAt,
        salesOpenAt,
        salesCloseAt
    );
  }

  // 수정
  public void update(
      Integer sessionNumber,
      OffsetDateTime performanceStartAt,
      OffsetDateTime performanceEndAt,
      OffsetDateTime salesOpenAt,
      OffsetDateTime salesCloseAt
  ) {

    // SCHEDULED 상태에서만 수정 가능
    validateModifiableStatus();

    Integer nextSessionNumber =
        sessionNumber != null ? sessionNumber : this.sessionNumber;

    OffsetDateTime nextPerformanceStartAt =
        performanceStartAt != null
            ? performanceStartAt
            : this.performanceStartAt;

    OffsetDateTime nextPerformanceEndAt =
        performanceEndAt != null
            ? performanceEndAt
            : this.performanceEndAt;

    OffsetDateTime nextSalesOpenAt =
        salesOpenAt != null ? salesOpenAt : this.salesOpenAt;

    OffsetDateTime nextSalesCloseAt =
        salesCloseAt != null ? salesCloseAt : this.salesCloseAt;


    //회차 시간 검증
    validateSchedule(
        nextSessionNumber,
        nextPerformanceStartAt,
        nextPerformanceEndAt,
        nextSalesOpenAt,
        nextSalesCloseAt
    );

    this.sessionNumber = nextSessionNumber;
    this.performanceStartAt = nextPerformanceStartAt;
    this.performanceEndAt = nextPerformanceEndAt;
    this.salesOpenAt = nextSalesOpenAt;
    this.salesCloseAt = nextSalesCloseAt;
  }

  // 공연 검증
  private void validateCreateEvent(Event event) {
    if (event.getStatus() != EventStatus.DRAFT) {
      throw new BusinessException(EventErrorCode.INVALID_EVENT_STATUS);
    }
  }

  // 회차 시간 검증
  private void validateSchedule(
      Integer sessionNumber,
      OffsetDateTime performanceStartAt,
      OffsetDateTime performanceEndAt,
      OffsetDateTime salesOpenAt,
      OffsetDateTime salesCloseAt
  ) {

    if (sessionNumber == null || sessionNumber < 1
        || performanceStartAt == null
        || performanceEndAt == null
        || salesOpenAt == null
        || salesCloseAt == null
        || !salesOpenAt.isBefore(salesCloseAt)
        || salesCloseAt.isAfter(performanceStartAt)
        || !performanceStartAt.isBefore(performanceEndAt)) {

      throw new BusinessException(
          EventErrorCode.INVALID_EVENT_SCHEDULE
      );
    }
  }

  // 판매 시작 및 좌석 재고 생성은 Service에서 검증
  private void validateModifiableStatus() {
    if (this.event.getStatus() != EventStatus.DRAFT
        || this.status != EventSessionStatus.SCHEDULED) {
      throw new BusinessException(EventErrorCode.EVENT_SESSION_MODIFICATION_NOT_ALLOWED);
    }
  }
}
