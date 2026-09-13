package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.domain.EventSessionStatus;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventSessionCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventSessionCreateResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventSessionInfoResponse;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.venue.domain.Venue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.tikitaka.platform.fixture.EventFixture.createEvent;
import static com.tikitaka.platform.fixture.EventSessionFixture.createEventSession;
import static com.tikitaka.platform.fixture.OrganizerFixture.activeOrganizer;
import static com.tikitaka.platform.fixture.VenueFixture.createVenue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EventSessionServiceTest {

  @Mock
  private OrganizerRepository organizerRepository;

  @Mock
  private EventRepository eventRepository;

  @Mock
  private EventSessionRepository eventSessionRepository;

  @Mock
  private Clock clock;

  @InjectMocks
  private EventSessionService eventSessionService;

  @Test
  void 판매중인_회차라면_예매용_공연정보를_반환() {

    Organizer organizer = mock(Organizer.class);
    Venue venue = mock(Venue.class);

    Event event = createEvent(organizer, venue);
    OffsetDateTime now = OffsetDateTime.now();

    EventSession eventSession = EventSession.create(
        event,
        1,
        now.plusDays(10),
        now.plusDays(10).plusHours(2),
        now.minusDays(1),
        now.plusDays(9),
        true
    );

    event.publish();
    event.openSales();

    UUID eventSessionId = eventSession.getId();

    given(eventSessionRepository.findByIdWithEvent(eventSessionId))
        .willReturn(Optional.of(eventSession));

    EventSessionInfoResponse response = eventSessionService.getReservationInfo(eventSessionId);

    assertThat(response.eventSessionId()).isEqualTo(eventSessionId);
    assertThat(response.eventId()).isEqualTo(event.getId());
    assertThat(response.eventTitle()).isEqualTo(event.getTitle());
  }

  @Test
  void 팬매중인_공연이_아니면_예매정보를_조회할_수_없다() {

    Organizer organizer = mock(Organizer.class);
    Venue venue = mock(Venue.class);

    // DRAFT 상태
    Event event = createEvent(organizer, venue);

    EventSession eventSession = createEventSession(event);

    given(eventSessionRepository.findByIdWithEvent(eventSession.getId()))
        .willReturn(Optional.of(eventSession));

    assertThatThrownBy(() ->
        eventSessionService.getReservationInfo(eventSession.getId())
    )
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(EventErrorCode.EVENT_NOT_RESERVABLE);
  }

  @Test
  void 활성_주최자는_회차를_등록할_수_있다() {
    Long userId = 1L;

    Organizer organizer = activeOrganizer(userId);

    Venue venue = createVenue();
    Event event = createEvent(organizer, venue);
    EventSessionCreateRequest request = createEventSessionRequest();

    given(organizerRepository.findByUserId(userId))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(event.getId(), organizer.getId()))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.save(any(EventSession.class)))
        .willAnswer(invocation ->
            invocation.getArgument(0, EventSession.class)
        );

    EventSessionCreateResponse response = eventSessionService.createEventSession(
        organizer.getUserId(),
        event.getId(), request
    );

    assertThat(response.sessionNumber()).isEqualTo(1);
    assertThat(response.status()).isEqualTo(EventSessionStatus.SCHEDULED.name());
  }

  private EventSessionCreateRequest createEventSessionRequest() {
    OffsetDateTime now = OffsetDateTime.now();

    return new EventSessionCreateRequest(
        now.plusDays(10),
        now.plusDays(10).plusHours(2),
        now.plusDays(1),
        now.plusDays(9),
        true
    );
  }
}