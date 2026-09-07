package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.infrastructure.SessionSectionPriceRepository;
import com.tikitaka.platform.event.presentation.dto.SessionSectionPricesResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.SessionSectionPricesCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.SessionSectionPricesCreateRequest.SectionPriceRequest;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SessionSectionPriceServiceTest {

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
    Long userId = 1L;
    UUID organizerId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID venueId = UUID.randomUUID();
    UUID sectionId = UUID.randomUUID();

    Organizer organizer = mock(Organizer.class);
    Event event = mock(Event.class);
    EventSession eventSession = mock(EventSession.class);
    Venue venue = mock(Venue.class);
    VenueSection venueSection = mock(VenueSection.class);

    SessionSectionPricesCreateRequest request =
        new SessionSectionPricesCreateRequest(
            List.of(
                new SectionPriceRequest(
                    sectionId,
                    "VIP",
                    150_000L,
                    true
                )
            )
        );

    given(organizer.getId()).willReturn(organizerId);
    given(event.getVenue()).willReturn(venue);
    given(venue.getId()).willReturn(venueId);

    given(venueSection.getId()).willReturn(sectionId);
    given(venueSection.getVenue()).willReturn(venue);
    given(venueSection.getName()).willReturn("VIP 구역");
    given(venueSection.isActive()).willReturn(true);

    given(organizerRepository.findByUserId(userId))
        .willReturn(Optional.of(organizer));

    given(eventRepository.findByIdAndOrganizerId(eventId, organizerId))
        .willReturn(Optional.of(event));

    given(eventSessionRepository.findByIdAndEventId(sessionId, eventId))
        .willReturn(Optional.of(eventSession));

    given(venueSectionRepository.findAllById(Set.of(sectionId)))
        .willReturn(List.of(venueSection));

    given(sessionSectionPriceRepository.saveAll(anyList()))
        .willAnswer(invocation -> invocation.getArgument(0));

    SessionSectionPricesResponse response =
        sessionSectionPriceService.replaceSectionPrices(
            userId,
            eventId,
            sessionId,
            request
        );

    assertThat(response.sessionId()).isEqualTo(sessionId);
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
}
