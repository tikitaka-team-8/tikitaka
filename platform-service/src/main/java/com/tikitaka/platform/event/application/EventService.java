package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.presentation.dto.*;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.venue.domain.Venue;
import com.tikitaka.platform.venue.exception.VenueErrorCode;
import com.tikitaka.platform.venue.infrastructure.VenueRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

  private final EventRepository eventRepository;
  private final OrganizerRepository organizerRepository;
  private final VenueRepository venueRepository;

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
    if (!organizer.isActive()) {
      throw new BusinessException(OrganizerErrorCode.INACTIVE_ORGANIZER);
    }

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
}
