package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.client.ticketing.TicketingSeatInventoryClient;
import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventStatusUpdateRequest;
import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventCreateResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventStatusUpdateResponse;
import com.tikitaka.platform.event.presentation.dto.query.PublicEventDetailResponse;
import com.tikitaka.platform.event.presentation.dto.query.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.query.PublicEventSummaryResponse;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.global.exception.CommonErrorCode;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.venue.domain.Venue;
import com.tikitaka.platform.venue.exception.VenueErrorCode;
import com.tikitaka.platform.venue.infrastructure.VenueRepository;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

  private final EventRepository eventRepository;
  private final OrganizerRepository organizerRepository;
  private final VenueRepository venueRepository;
  private final EventPublicationValidator eventPublicationValidator;
  private final TicketingSeatInventoryClient ticketingSeatInventoryClient;

  // 공개 공연 목록 조회
  public Page<PublicEventSummaryResponse> getPublicEvents(
      PublicEventListRequest request
  ) {

    PublicEventSearchCondition condition = PublicEventSearchCondition.of(
        request.keyword(),
        request.venueId()
    );

    return eventRepository.findPublicEvents(
        condition,
        request.toPageRequest()
    )
        .map(PublicEventSummaryResponse::from);
  }

  // 공개 공연 상세 조회
  public PublicEventDetailResponse getPublicEvent(UUID eventId) {

    Event event = eventRepository.findPublicEventDetail(
            eventId,
            EventStatus.publicStatuses()
        )
        .orElseThrow(() ->
            new BusinessException(
                EventErrorCode.EVENT_NOT_FOUND
            )
        );
    return PublicEventDetailResponse.from(event);
  }

  @Transactional
  public EventCreateResponse createEvent(
      Long userId,
      EventCreateRequest request
  ) {
    Organizer organizer = organizerRepository.findByUserId(userId)
        .orElseThrow(() ->
            new BusinessException(OrganizerErrorCode.ORGANIZER_NOT_FOUND)
        );

    // Active 상태인지
    organizer.validateActive();

    Venue venue = venueRepository.findById(request.venueId())
        .orElseThrow(() ->
            new BusinessException(VenueErrorCode.VENUE_NOT_FOUND)
        );

    // Active 상태인지
    if (!venue.isActive()) {
      throw new BusinessException(VenueErrorCode.INACTIVE_VENUE);
    }

    Event event = Event.create(
        organizer,
        venue,
        request.title(),
        request.description(),
        request.runningTimeMinutes()
    );

    Event savedEvent = eventRepository.save(event);

    return EventCreateResponse.from(savedEvent);
  }

  // 공연 상태 변경
  @Transactional
  public EventStatusUpdateResponse changeStatus(
      Long userId,
      UUID eventId,
      EventStatusUpdateRequest request
  ) {

    Organizer organizer = organizerRepository.findByUserId(userId)
        .orElseThrow(() ->
            new BusinessException(OrganizerErrorCode.ORGANIZER_NOT_FOUND)
        );

    organizer.validateActive();

    Event event = eventRepository.findByIdAndOrganizerId(eventId, organizer.getId())
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_NOT_FOUND)
        );

    EventStatus previousStatus = event.getStatus();
    EventStatus targetStatus =
        EventStatus.valueOf(request.targetStatus().name());

    if (targetStatus == EventStatus.CANCELED) {

      event.changeStatus(EventStatus.CANCELED);

      return new EventStatusUpdateResponse(
          event.getId(),
          previousStatus,
          event.getStatus(),
          0,
          0,
          0
      );
    }

    // 공연 상태 변경 및 좌석 재고 생성
    return publish(event, previousStatus);
  }

  // 공연 공개
  private EventStatusUpdateResponse publish(
      Event event,
      EventStatus previousStatus
  ) {

    // 모든 회차를 검증한 후 좌석 스냅샷 생성
    EventPublicationPlan publicationPlan = eventPublicationValidator.validateAndCreate(
        event,
        OffsetDateTime.now()
    );

    int inventorySessionCount = 0;
    int totalCreatedSeatCount = 0;
    int totalSkippedSeatCount = 0;

    // Ticketing Service에 회차별 좌석 생성 요청
    for (EventPublicationPlan.SessionSeats session
        : publicationPlan.sessions()) {

      CreateScheduleSeatsRequest request =
          CreateScheduleSeatsRequest.from(
              publicationPlan.venueId(),
              session
          );

      // Feign 호출
      CreateScheduleSeatsResponse response =
          requestScheduleSeatCreation(session.eventSessionId(), request);

      totalCreatedSeatCount += response.createdCount();
      totalSkippedSeatCount += response.skippedCount();
      inventorySessionCount++;
    }

    // UPCOMING
    event.publish();

    return new EventStatusUpdateResponse(
        event.getId(),
        previousStatus,
        event.getStatus(),
        inventorySessionCount,
        totalCreatedSeatCount,
        totalSkippedSeatCount
    );
  }

  private CreateScheduleSeatsResponse requestScheduleSeatCreation(
      UUID eventSessionId,
      CreateScheduleSeatsRequest request
  ) {

    try {

      return ticketingSeatInventoryClient
          .createScheduleSeats(
              eventSessionId,
              request
          );

    } catch (RetryableException exception) {
      throw new BusinessException(
          CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT
      );
    } catch (FeignException exception) {
      throw new BusinessException(
          CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE
      );
    }
  }
}
