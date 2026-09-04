package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.application.query.PublicEventSummaryResult;
import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.presentation.dto.PublicEventDetailResponse;
import com.tikitaka.platform.event.presentation.dto.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.PublicEventSummaryResponse;
import com.tikitaka.platform.organizer.application.command.OrganizerCreateCommand;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.domain.OrganizerStatus;
import com.tikitaka.platform.venue.domain.Venue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

  @Test
  void 공개_공연_상세_조회한다() {
    Long userId = 1L;
    UUID eventId = UUID.randomUUID();

    Organizer organizer = createOrganizer(userId);
    Venue venue = createVenue();
    Event event = createPublicEvent(organizer, venue);

    ReflectionTestUtils.setField(event, "id", eventId);

    given(eventRepository.findPublicEventDetail(
        event.getId(),
        EventStatus.publicStatuses()
    )).willReturn(Optional.of(event));

    PublicEventDetailResponse response =
        eventService.getPublicEvent(eventId);

    assertThat(response.eventId()).isEqualTo(eventId);
    assertThat(response.title()).isEqualTo(event.getTitle());
    assertThat(response.description()).isEqualTo(event.getDescription());
    assertThat(response.venue().address()).isEqualTo(venue.getAddress());
    assertThat(response.venue().name()).isEqualTo(venue.getName());
  }

  private Event createPublicEvent(Organizer organizer, Venue venue) {
    Event event = Event.create(
        organizer,
        venue,
        "티키타카 콘서트",
        "설명",
        180
    );

    event.publish();
    return event;
  }

  private Venue createVenue() {
    return Venue.create(
        "티키타카 공연장",
        "25812",
        "서울특별시 티키타가",
        "티키타카호",
        "010-1234-5678"
    );
  }


  private PublicEventListRequest publicEventListRequest(UUID venueId) {

    return new PublicEventListRequest(
        "콘서트",
        venueId,
        0,
        20
    );
  }

  private Organizer createOrganizer(Long userId) {
    OrganizerCreateCommand command = createOrganizerCommand(userId);
    Organizer organizer = Organizer.create(
        command.userId(),
        command.name(),
        command.representativeName(),
        command.contactEmail(),
        command.contactPhone(),
        command.description()
    );
    organizer.changeStatus(OrganizerStatus.ACTIVE, OffsetDateTime.now());

    return organizer;
  }

  private static OrganizerCreateCommand createOrganizerCommand(Long userId) {
    return new OrganizerCreateCommand(
        userId,
        "티키타카",
        "키키",
        "organizer@tikitaka.com",
        "010-1234-5677",
        "공연 기획 운영"
    );
  }

}