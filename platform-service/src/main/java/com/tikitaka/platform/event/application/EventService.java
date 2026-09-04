package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.presentation.dto.PublicEventDetailResponse;
import com.tikitaka.platform.event.presentation.dto.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.PublicEventSummaryResponse;
import com.tikitaka.platform.global.exception.BusinessException;
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
}
