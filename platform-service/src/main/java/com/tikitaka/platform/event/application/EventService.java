package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.presentation.dto.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.PublicEventSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

  private final EventRepository eventRepository;

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
}
