package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.EventPublicationPlan.SeatSnapshot;
import com.tikitaka.platform.event.application.EventPublicationPlan.SessionSeats;
import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.domain.SessionSectionPrice;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.venue.domain.VenueSeat;
import com.tikitaka.platform.venue.infrastructure.VenueSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventPublicationValidator {

  private final EventSessionRepository eventSessionRepository;
  private final VenueSeatRepository venueSeatRepository;

  public EventPublicationPlan validateAndCreate(
      Event event,
      OffsetDateTime now
  ) {

    if (event.getStatus() != EventStatus.DRAFT) {
      throw new BusinessException(
          EventErrorCode.INVALID_EVENT_STATUS
      );
    }


    // 공연 회차
    List<EventSession> sessions =
        eventSessionRepository.findAllForPublication(event.getId());

    // 공연에 회차가 한 개 이상 있는가
    if (sessions.isEmpty()) {
      throw new BusinessException(
          EventErrorCode.EVENT_SESSION_REQUIRED
      );
    }

    // 회차 SCHEDULED, 시간 검증
    sessions.forEach(session ->
        session.validatePublishable(now));

    UUID venueId = event.getVenue().getId();

    List<VenueSeat> activeSeats =
        venueSeatRepository.findAllActiveByVenueId(venueId);

    // 공연장에 활성 좌석이 존재하는가
    if (activeSeats.isEmpty()) {
      throw new BusinessException(
          EventErrorCode.ACTIVE_VENUE_SEAT_REQUIRED
      );
    }

    // 좌석의 구역 ID
    Set<UUID> activeSeatSectionIds = activeSeats.stream()
        .map(seat -> seat.getVenueSection().getId())
        .collect(Collectors.toSet());

    List<SessionSeats> sessionSeats = sessions.stream()
        .map(session -> createSessionSeats(
            session,
            venueId,
            activeSeatSectionIds,
            activeSeats
        ))
        .toList();

    return new EventPublicationPlan(
        venueId,
        sessionSeats
    );

  }

  private SessionSeats createSessionSeats(
      EventSession session,
      UUID venueId,
      Set<UUID> activeSeatSectionIds,
      List<VenueSeat> activeSeats
  ) {

    List<SessionSectionPrice> prices = session.getSectionPrices();

    // 회차에 구역별 가격 정책이 있는가
    if (prices.isEmpty()) {
      throw new BusinessException(
          EventErrorCode.SECTION_PRICE_REQUIRED
      );
    }

    // 가격 정책의 구역이 해당 공연장에 속하는가
    boolean hasVenueMismatch  = prices.stream()
        .anyMatch(price ->
            !price.getVenueSection()
                .getVenue()
                .getId()
                .equals(venueId)
        );

    if (hasVenueMismatch) {
      throw new BusinessException(
          EventErrorCode.SECTION_PRICE_VENUE_MISMATCH
      );
    }

    Map<UUID, SessionSectionPrice> priceSection = prices.stream()
        .collect(Collectors.toMap(price ->
                price.getVenueSection().getId(),
            price -> price
        ));

    // 활성 좌석이 있는 모든 구역에 가격이 있는지
    if (!priceSection.keySet().containsAll(activeSeatSectionIds)) {
      throw new BusinessException(
          EventErrorCode.SECTION_PRICE_REQUIRED
      );
    }

    // sales_enabled = true인 가격만 재고 생성
    Map<UUID, SessionSectionPrice> enabledPriceSection = prices.stream()
        .filter(SessionSectionPrice::isSalesEnabled)
        .filter(price ->
            activeSeatSectionIds.contains(
                price.getVenueSection().getId()
            )
        )
        .collect(Collectors.toMap(
            price -> price.getVenueSection().getId(),
            price -> price
        ));


    // 한 개 이상의 판매 가능 가격 정책이 있는가
    if (enabledPriceSection.isEmpty()) {
      throw new BusinessException(
          EventErrorCode.SELLABLE_SECTION_PRICE_REQUIRED
      );
    }

    // 좌석 재고 생성에 보낼 snapshot 생성
    List<SeatSnapshot> snapshots = activeSeats.stream()
        .filter(seat ->
            enabledPriceSection.containsKey(seat.getVenueSection().getId()
            )
        )
        .map(seat -> {
          SessionSectionPrice price = enabledPriceSection.get(
              seat.getVenueSection().getId()
          );

          return new SeatSnapshot(
              seat.getId(),
              seat.getVenueSection().getName(),
              seat.getRowLabel(),
              seat.getSeatNumber(),
              price.getSeatGrade(),
              price.getPriceAmount()
          );
        })
        .toList();

    if (snapshots.isEmpty()) {
      throw new BusinessException(
          EventErrorCode.SELLABLE_SECTION_PRICE_REQUIRED
      );
    }

    return new SessionSeats(
        session.getId(),
        snapshots
    );
  }
}
