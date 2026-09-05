package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.presentation.dto.*;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventSessionService {

  private final EventRepository eventRepository;
  private final EventSessionRepository eventSessionRepository;
  private final OrganizerRepository organizerRepository;

  public PublicEventSessionDetailResponse getPublicEventSession(
      UUID eventId,
      UUID sessionId
  ) {

    Event event = eventRepository.findById(eventId)
        .filter(Event::isPubliclyVisible)
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_NOT_FOUND)
        );

    // 공개 회차 CANCELED 상태 제외
    EventSession session = eventSessionRepository.findDetailByIdAndEventId(sessionId, event.getId())
        .filter(es ->
            es.isPubliclyVisible())
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_SESSION_NOT_FOUND)
        );

    return PublicEventSessionDetailResponse.from(session);
  }

  // Queue 대기열
  @Transactional(readOnly = true)
  public QueueSalesStatusResponse getQueueSalesStatus(UUID sessionId) {

    EventSession eventSession = eventSessionRepository.findById(sessionId)
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_SESSION_NOT_FOUND)
        );

    if (!eventSession.getEvent().getStatus().allowsQueueSale()) {
      throw new BusinessException(EventErrorCode.EVENT_SESSION_NOT_FOUND);
    }

    return QueueSalesStatusResponse.from(eventSession);
  }

  // 공연 정보 조회
  public EventSessionInfoResponse getReservationInfo(UUID eventSessionId) {
    EventSession eventSession = eventSessionRepository.findByIdWithEvent(eventSessionId)
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_SESSION_NOT_FOUND)
        );

    // 예매가 가능한지 검증
    validateReservable(eventSession);
    return EventSessionInfoResponse.from(eventSession);
  }

  // 회차 생성
  @Transactional
  public EventSessionCreateResponse createEventSession(
      Long userId,
      UUID eventId,
      EventSessionCreateRequest request
  ) {

    Organizer organizer = organizerRepository.findByUserId(userId)
        .orElseThrow(() ->
            new BusinessException(OrganizerErrorCode.ORGANIZER_NOT_FOUND)
        );

    Event event = eventRepository.findByIdAndOrganizerId(eventId, organizer.getId())
        .orElseThrow(() ->
          new BusinessException(EventErrorCode.EVENT_NOT_FOUND)
        );

    // Active 상태인지
    organizer.validateActive();

    // DRAFT 상태에서만 가능
    event.validateSessionCreatable();
    // 시간 검증
    validateTime(request);

    // number 확인
    int nextSessionNumber =
        eventSessionRepository.findMaxSessionNumber(eventId) + 1;

    EventSession eventSession = EventSession.create(
        event,
        nextSessionNumber,
        request.performanceStartAt(),
        request.performanceEndAt(),
        request.salesOpenAt(),
        request.salesCloseAt(),
        request.queueEnabled()
    );
    EventSession savedSession = eventSessionRepository.save(eventSession);

    return EventSessionCreateResponse.from(savedSession);
  }

  private void validateTime(EventSessionCreateRequest request) {
    boolean invalid =
        !request.performanceStartAt().isBefore(request.performanceEndAt())
            || !request.salesOpenAt().isBefore(request.salesCloseAt())
            || request.salesCloseAt().isAfter(request.performanceStartAt())
            || !OffsetDateTime.now().isBefore(request.salesOpenAt());

    if (invalid) {
      throw new BusinessException(EventErrorCode.INVALID_EVENT_SCHEDULE);
    }
  }

  private void validateReservable(EventSession eventSession) {
    Event event = eventSession.getEvent();
    OffsetDateTime now = OffsetDateTime.now();

    if (!event.getStatus().isReservable()) {
      throw new BusinessException(EventErrorCode.EVENT_NOT_RESERVABLE);
    }

    if (!eventSession.isReservableAt(now)) {
      throw new BusinessException(EventErrorCode.EVENT_SESSION_NOT_RESERVABLE);
    }
  }
}

