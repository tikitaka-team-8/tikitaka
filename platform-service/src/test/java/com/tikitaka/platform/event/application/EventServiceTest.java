package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.application.query.PublicEventSummaryResult;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.presentation.dto.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.PublicEventSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

  @Mock
  private EventRepository eventRepository;

  @InjectMocks
  private EventService eventService;

  @Test
  void 공개_공연_목록을_조회한다() {
    UUID eventId = UUID.randomUUID();
    UUID venueId = UUID.randomUUID();

    PublicEventListRequest request = publicEventListRequest(venueId);

    PageRequest pageable = PageRequest.of(0, 20);

    PublicEventSummaryResult result = new PublicEventSummaryResult(
        eventId,
        "티키타카 콘서트",
        "티키타카 아트홀",
        EventStatus.UPCOMING
    );

    given(eventRepository.findPublicEvents(
        new PublicEventSearchCondition("콘서트", venueId),
        pageable
    )).willReturn(
        new PageImpl<>(
            List.of(result),
            pageable,
            1
        )
    );

    Page<PublicEventSummaryResponse> responses =
        eventService.getPublicEvents(request);

    assertThat(responses.getTotalElements()).isEqualTo(1);
    assertThat(responses.getContent()).hasSize(1);

    PublicEventSummaryResponse response = responses.getContent().getFirst();

    assertThat(response.eventId()).isEqualTo(eventId);
    assertThat(response.title()).isEqualTo(result.title());
    assertThat(response.venueName()).isEqualTo(result.venueName());
    assertThat(response.status()).isEqualTo(result.status().name());
  }

  private PublicEventListRequest publicEventListRequest(UUID venueId) {

    return new PublicEventListRequest(
        "콘서트",
        venueId,
        0,
        20
    );
  }

}