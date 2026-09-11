package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.infrastructure.client.ticketing.TicketingSeatInventoryClient;
import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsResponse;
import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventStatusUpdateRequest;
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
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

  private final EventRepository eventRepository;
  private final OrganizerRepository organizerRepository;
  private final VenueRepository venueRepository;
  private final EventPublicationValidator eventPublicationValidator;
  private final TicketingSeatInventoryClient ticketingSeatInventoryClient;
  private final EventSessionRepository eventSessionRepository;

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

    return switch (request.targetStatus()) {
      case UPCOMING -> publish(event, previousStatus);
      case CANCELED -> cancel(event, previousStatus);

    };
  }

  // 공연 취소
  private EventStatusUpdateResponse cancel(
      Event event,
      EventStatus previousStatus
  ) {

    // 공연 회차 상태 취소
    event.cancel();
    cancelEventSessions(event.getId());

    return new EventStatusUpdateResponse(
        event.getId(),
        previousStatus,
        event.getStatus(),
        0,
        0,
        0
    );
  }

  // 공연회차 상태 취소
  private void cancelEventSessions(UUID eventId) {

    List<EventSession> sessions =
        eventSessionRepository.findAllByEventId(eventId);

    sessions.forEach(EventSession::cancel);
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

    List<CreateScheduleSeatsRequest> requests = publicationPlan.sessions().stream()
        .map(session -> CreateScheduleSeatsRequest.from(
            publicationPlan.venueId(),
            session
        ))
        .toList();

    // Feign 호출 전체 목록 한번에 전달
    List<CreateScheduleSeatsResponse> responses =
        requestEventSeatInventoryCreation(requests);

    // 모든 회차가 정상 처리되었는지 확인
    validateSeatInventoryResponses(requests, responses);

    int totalCreatedCount = responses.stream()
        .mapToInt(CreateScheduleSeatsResponse::createdCount)
        .sum();

    int totalSkippedCount = responses.stream()
        .mapToInt(CreateScheduleSeatsResponse::skippedCount)
        .sum();

    // UPCOMING
    event.publish();

    return new EventStatusUpdateResponse(
        event.getId(),
        previousStatus,
        event.getStatus(),
        responses.size(),
        totalCreatedCount,
        totalSkippedCount
    );
  }

  private void validateSeatInventoryResponses(
      List<CreateScheduleSeatsRequest> requests,
      List<CreateScheduleSeatsResponse> responses
  ) {

    if (responses.size() != requests.size()) {
      throw new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE);
    }

    Map<UUID, Integer> requestedSeatCounts = new HashMap<>();

    for (CreateScheduleSeatsRequest request : requests) {
      requestedSeatCounts.put(
          request.eventSessionId(),
          request.seats().size()
      );
    }

    Set<UUID> processedSessionIds = new HashSet<>();

    // 회차별 요청 수와 처리 결과 수 확인
    for (CreateScheduleSeatsResponse response : responses) {

      UUID sessionId = response.eventSessionId();

      Integer requestedSeatCount =
          requestedSeatCounts.get(response.eventSessionId());

      // 같은 회차 응답 중복
      if (!processedSessionIds.add(sessionId)) {
        throw new BusinessException(
            CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE
        );
      }

      long processedSeatCount =
          (long) response.createdCount() + response.skippedCount();


      if (processedSeatCount != requestedSeatCount.longValue()) {
        throw new BusinessException(
            CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE
        );
      }
    }
  }

  private List<CreateScheduleSeatsResponse> requestEventSeatInventoryCreation(
      List<CreateScheduleSeatsRequest> requests
  ) {

    try {

      return ticketingSeatInventoryClient
          .createScheduleSeats(requests);

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
