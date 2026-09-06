package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.presentation.dto.EventSessionInfoResponse;
import com.tikitaka.platform.event.presentation.dto.PublicEventSessionDetailResponse;
import com.tikitaka.platform.event.presentation.dto.QueueSalesStatusResponse;
import com.tikitaka.platform.global.exception.BusinessException;
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

