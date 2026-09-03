package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.presentation.dto.PublicEventSessionDetailResponse;
import com.tikitaka.platform.event.presentation.dto.QueueSalesStatusResponse;
import com.tikitaka.platform.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
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
  public QueueSalesStatusResponse getQueueSalseStatus(UUID sessionId) {

    EventSession eventSession = eventSessionRepository.findById(sessionId)
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_SESSION_NOT_FOUND)
        );

    return QueueSalesStatusResponse.from(eventSession);
  }
}
