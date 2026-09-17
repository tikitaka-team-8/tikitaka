package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.*;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.infrastructure.SessionSectionPriceRepository;
import com.tikitaka.platform.event.presentation.dto.SessionSectionPricesResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.request.SessionSectionPricesCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.request.SessionSectionPricesCreateRequest.SectionPriceRequest;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.venue.domain.Venue;
import com.tikitaka.platform.venue.domain.VenueSection;
import com.tikitaka.platform.venue.infrastructure.VenueSectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.tikitaka.platform.fixture.EventFixture.createEvent;
import static com.tikitaka.platform.fixture.EventSessionFixture.createEventSession;
import static com.tikitaka.platform.fixture.OrganizerFixture.activeOrganizer;
import static com.tikitaka.platform.fixture.VenueFixture.createVenue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionSectionPriceServiceTest {

  private static final Long USER_ID = 1L;
  private static final UUID ORGANIZER_ID = UUID.randomUUID();
  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID EVENT_SESSION_ID = UUID.randomUUID();
  private static final UUID VENUE_ID = UUID.randomUUID();
  private static final UUID SECTION_ID = UUID.randomUUID();

  @Mock
  private OrganizerRepository organizerRepository;

  @Mock
  private EventRepository eventRepository;

  @Mock
  private EventSessionRepository eventSessionRepository;

  @Mock
  private VenueSectionRepository venueSectionRepository;

  @Mock
  private SessionSectionPriceRepository sessionSectionPriceRepository;

  @InjectMocks
  private SessionSectionPriceService sessionSectionPriceService;


  @Test
  void 회차별_좌석_등급_가격을_설정() {

    Organizer organizer = mock(Organizer.class);
    Event event = mock(Event.class);
    EventSession eventSession = mock(EventSession.class);
    Venue venue = mock(Venue.class);
    VenueSection venueSection = mock(VenueSection.class);

    SessionSectionPricesCreateRequest request =
        new SessionSectionPricesCreateRequest(
            List.of(
                new SectionPriceRequest(
                    SECTION_ID,
                    "VIP",
                    150_000L,
                    true
                )
            )
        );

    given(organizer.getId()).willReturn(ORGANIZER_ID);
    given(event.getVenue()).willReturn(venue);
    given(venue.getId()).willReturn(VENUE_ID);

    given(venueSection.getId()).willReturn(SECTION_ID);
    given(venueSection.getVenue()).willReturn(venue);
    given(venueSection.getName()).willReturn("VIP 구역");
    given(venueSection.isActive()).willReturn(true);

    given(organizerRepository.findByUserId(USER_ID))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(EVENT_ID, ORGANIZER_ID))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.findByIdAndEventId(EVENT_SESSION_ID, EVENT_ID))
        .willReturn(Optional.of(eventSession));

    given(venueSectionRepository.findAllById(Set.of(SECTION_ID)))
        .willReturn(List.of(venueSection));

    given(sessionSectionPriceRepository.saveAll(anyList()))
        .willAnswer(invocation -> invocation.getArgument(0));

    SessionSectionPricesResponse response =
        sessionSectionPriceService.replaceSectionPrices(
            USER_ID,
            EVENT_ID,
            EVENT_SESSION_ID,
            request
        );

    assertThat(response.sessionId()).isEqualTo(EVENT_SESSION_ID);
    assertThat(response.sectionPrices()).hasSize(1);
    assertThat(response.sectionPrices().getFirst().sectionName())
        .isEqualTo("VIP 구역");
    assertThat(response.sectionPrices().getFirst().seatGrade())
        .isEqualTo("VIP");
    assertThat(response.sectionPrices().getFirst().priceAmount())
        .isEqualTo(150_000L);
    assertThat(response.sectionPrices().getFirst().salesEnabled())
        .isTrue();
  }

  @Test
  void 같은_공연장_구역을_중복으로_설정할_수_없다() {
    Long userId = 1L;
    UUID organizerId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID sectionId = UUID.randomUUID();

    Organizer organizer = mock(Organizer.class);
    Event event = mock(Event.class);
    EventSession eventSession = mock(EventSession.class);

    SessionSectionPricesCreateRequest request =
        new SessionSectionPricesCreateRequest(
            List.of(
                new SectionPriceRequest(
                    sectionId,
                    "VIP",
                    150_000L,
                    true
                ),
                new SectionPriceRequest(
                    sectionId,
                    "R",
                    100_000L,
                    true
                )
            )
        );

    given(organizer.getId()).willReturn(organizerId);

    given(organizerRepository.findByUserId(userId))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(eventId, organizerId))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.findByIdAndEventId(sessionId, eventId))
        .willReturn(Optional.of(eventSession));

    assertThatThrownBy(() ->
        sessionSectionPriceService.replaceSectionPrices(
            userId,
            eventId,
            sessionId,
            request
        )
    )
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(EventErrorCode.DUPLICATE_SECTION_PRICE);
  }

  @Test
  void 회차별_좌석_등급_가격을_조회한다() {

    Organizer organizer = mock(Organizer.class);
    Event event = mock(Event.class);
    EventSession eventSession = mock(EventSession.class);
    VenueSection venueSection = mock(VenueSection.class);
    SessionSectionPrice sectionPrice = mock(SessionSectionPrice.class);

    given(organizer.getId()).willReturn(ORGANIZER_ID);
    given(event.getId()).willReturn(EVENT_ID);
    given(eventSession.getId()).willReturn(EVENT_SESSION_ID);

    given(venueSection.getId()).willReturn(SECTION_ID);
    given(venueSection.getName()).willReturn("VIP 구역");

    given(sectionPrice.getVenueSection()).willReturn(venueSection);
    given(sectionPrice.getSeatGrade()).willReturn("VIP");
    given(sectionPrice.getPriceAmount()).willReturn(150_000L);
    given(sectionPrice.isSalesEnabled()).willReturn(true);

    given(organizerRepository.findByUserId(USER_ID))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(EVENT_ID, ORGANIZER_ID))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.findByIdAndEventId(EVENT_SESSION_ID, EVENT_ID))
        .willReturn(Optional.of(eventSession));

    given(sessionSectionPriceRepository.findAllByEventSessionId(EVENT_SESSION_ID))
        .willReturn(List.of(sectionPrice));

    SessionSectionPricesResponse response =
        sessionSectionPriceService.getSectionPrices(
            USER_ID,
            EVENT_ID,
            EVENT_SESSION_ID
        );

    assertThat(response.sessionId()).isEqualTo(EVENT_SESSION_ID);
    assertThat(response.sectionPrices()).hasSize(1);

    assertThat(response.sectionPrices().getFirst().venueSectionId())
        .isEqualTo(SECTION_ID);
    assertThat(response.sectionPrices().getFirst().sectionName())
        .isEqualTo("VIP 구역");
    assertThat(response.sectionPrices().getFirst().seatGrade())
        .isEqualTo("VIP");
    assertThat(response.sectionPrices().getFirst().priceAmount())
        .isEqualTo(150_000L);
    assertThat(response.sectionPrices().getFirst().salesEnabled())
        .isTrue();

  }

  @Test
  void 공연이_DRAFT_상태면_가격을_수정할_수_있다() {

    Organizer organizer = activeOrganizer(USER_ID);
    Venue venue = createVenue();
    Event event = createEvent(organizer, venue);
    EventSession eventSession = createEventSession(event);

    VenueSection section =
        VenueSection.create(venue, "VIP 구역", "1층", 1, true);

    ReflectionTestUtils.setField(organizer, "id", ORGANIZER_ID);
    ReflectionTestUtils.setField(venue, "id", VENUE_ID);
    ReflectionTestUtils.setField(event, "id", EVENT_ID);
    ReflectionTestUtils.setField(eventSession, "id", EVENT_SESSION_ID);
    ReflectionTestUtils.setField(section, "id", SECTION_ID);

    SessionSectionPricesCreateRequest request =
        priceRequest(180_000L);

    given(organizerRepository.findByUserId(USER_ID))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(EVENT_ID, ORGANIZER_ID))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.findByIdAndEventId(EVENT_SESSION_ID, EVENT_ID))
        .willReturn(Optional.of(eventSession));

    given(venueSectionRepository.findAllById(Set.of(SECTION_ID)))
        .willReturn(List.of(section));

    given(sessionSectionPriceRepository.saveAll(anyList()))
        .willAnswer(invocation -> invocation.getArgument(0));


    SessionSectionPricesResponse response =
        sessionSectionPriceService.replaceSectionPrices(
            USER_ID, EVENT_ID, EVENT_SESSION_ID, request
        );


    assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    assertThat(eventSession.getStatus())
        .isEqualTo(EventSessionStatus.SCHEDULED);

    assertThat(response.sessionId()).isEqualTo(EVENT_SESSION_ID);
    assertThat(response.sectionPrices()).hasSize(1);
    assertThat(response.sectionPrices().getFirst().priceAmount())
        .isEqualTo(180_000L);
  }

  @Test
  void 공연이_UPCOMING으로_변경되면_가격을_수정할_수_없다() {

    Organizer organizer = activeOrganizer(USER_ID);
    Venue venue = createVenue();
    Event event = createEvent(organizer, venue);
    EventSession eventSession = createEventSession(event);

    ReflectionTestUtils.setField(organizer, "id", ORGANIZER_ID);
    ReflectionTestUtils.setField(event, "id", EVENT_ID);
    ReflectionTestUtils.setField(eventSession, "id", EVENT_SESSION_ID);

    event.publish();

    given(organizerRepository.findByUserId(USER_ID))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(EVENT_ID, ORGANIZER_ID))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.findByIdAndEventId(EVENT_SESSION_ID, EVENT_ID))
        .willReturn(Optional.of(eventSession));


    SessionSectionPricesCreateRequest request =
        priceRequest(180_000L);

    assertThat(event.getStatus()).isEqualTo(EventStatus.UPCOMING);

    assertThatThrownBy(() ->
        sessionSectionPriceService.replaceSectionPrices(
            USER_ID, EVENT_ID, EVENT_SESSION_ID, request
        )
    )
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(EventErrorCode.EVENT_SESSION_MODIFICATION_NOT_ALLOWED);

    verify(sessionSectionPriceRepository, never())
        .deleteAllByEventSessionId(any());

    verify(sessionSectionPriceRepository, never())
        .saveAll(anyList());
  }



  @Test
  void 취소된_회차의_가격은_수정할_수_없다() {
    Organizer organizer = activeOrganizer(USER_ID);
    Venue venue = createVenue();
    Event event = createEvent(organizer, venue);
    EventSession eventSession = createEventSession(event);

    ReflectionTestUtils.setField(organizer, "id", ORGANIZER_ID);
    ReflectionTestUtils.setField(event, "id", EVENT_ID);
    ReflectionTestUtils.setField(eventSession, "id", EVENT_SESSION_ID);

    given(organizerRepository.findByUserId(USER_ID))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(EVENT_ID, ORGANIZER_ID))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.findByIdAndEventId(EVENT_SESSION_ID, EVENT_ID))
        .willReturn(Optional.of(eventSession));

    eventSession.cancel();

    SessionSectionPricesCreateRequest request =
        priceRequest(180_000L);


    assertThatThrownBy(() ->
        sessionSectionPriceService.replaceSectionPrices(
            USER_ID, EVENT_ID, EVENT_SESSION_ID, request
        )
    )
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(EventErrorCode.EVENT_SESSION_MODIFICATION_NOT_ALLOWED);

    verify(sessionSectionPriceRepository, never())
        .deleteAllByEventSessionId(any());

    verify(sessionSectionPriceRepository, never())
        .saveAll(anyList());
  }

  private static SessionSectionPricesCreateRequest priceRequest(Long price) {
    return new SessionSectionPricesCreateRequest(
        List.of(
            new SectionPriceRequest(
                SECTION_ID, "VIP", price, true
            )
        )
    );
  }

}
