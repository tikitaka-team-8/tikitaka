package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.presentation.dto.EventSessionInfoResponse;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.domain.Organizer;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EventSessionServiceTest {

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

    EventSession eventSession = createEventSession(event);

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

  private static EventSession createEventSession(Event event) {
    return EventSession.create(
        event,
        1,
        OffsetDateTime.parse("2026-09-10T19:00:00+09:00"),
        OffsetDateTime.parse("2026-09-10T21:00:00+09:00"),
        OffsetDateTime.parse("2026-09-01T12:00:00+09:00"),
        OffsetDateTime.parse("2026-09-10T18:00:00+09:00"),
        true
    );
  }
}