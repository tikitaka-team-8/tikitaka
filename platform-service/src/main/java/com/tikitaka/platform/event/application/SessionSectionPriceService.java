package com.tikitaka.platform.event.application;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;
import com.tikitaka.platform.event.domain.SessionSectionPrice;
import com.tikitaka.platform.event.exception.EventErrorCode;
import com.tikitaka.platform.event.infrastructure.EventRepository;
import com.tikitaka.platform.event.infrastructure.EventSessionRepository;
import com.tikitaka.platform.event.infrastructure.SessionSectionPriceRepository;
import com.tikitaka.platform.event.presentation.dto.SessionSectionPricesResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.SessionSectionPricesCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.SessionSectionPricesCreateRequest.SectionPriceRequest;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.venue.domain.VenueSection;
import com.tikitaka.platform.venue.exception.VenueErrorCode;
import com.tikitaka.platform.venue.infrastructure.VenueSectionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionSectionPriceService {

  private final OrganizerRepository organizerRepository;
  private final EventRepository eventRepository;
  private final EventSessionRepository eventSessionRepository;
  private final VenueSectionRepository venueSectionRepository;
  private final SessionSectionPriceRepository sessionSectionPriceRepository;

  // 가격 설정
  public SessionSectionPricesResponse replaceSectionPrices(
      Long userId,
      UUID eventId,
      UUID sessionId,
      SessionSectionPricesCreateRequest request
  ) {

    // 주최자 확인
    Organizer organizer = organizerRepository.findByUserId(userId)
        .orElseThrow(() ->
            new BusinessException(OrganizerErrorCode.ORGANIZER_NOT_FOUND)
        );

    organizer.validateActive();

    // 공연 확인
    Event event = eventRepository.findByIdAndOrganizerId(eventId, organizer.getId())
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_NOT_FOUND)
        );

    // 공연에 속한 회차 검증
    EventSession eventSession = eventSessionRepository.findByIdAndEventId(sessionId, eventId)
        .orElseThrow(() ->
            new BusinessException(EventErrorCode.EVENT_SESSION_NOT_FOUND)
        );

    // 가격 수정 가능 상태 검증
    eventSession.validateSectionPriceModifiable();

    // 중복 가격 설정 검증
    validateDuplicateSections(request);

    // 1. 기존 가격 삭제
    // 2. 요청 가격 생성
    Map<UUID, VenueSection> sectionMap = loadAndValidateVenueSections(event, request);

    sessionSectionPriceRepository.deleteAllByEventSessionId(sessionId);

    // SectionPrice 객체 생성
    List<SessionSectionPrice> newPrices =
        request.sectionPrices().stream()
            .map(item -> createSectionPrice(
                eventSession,
                sectionMap.get(item.venueSectionId()),
                item
            ))
            .toList();

    List<SessionSectionPrice> savedPrices = sessionSectionPriceRepository.saveAll(newPrices);

    return SessionSectionPricesResponse.from(
        sessionId,
        savedPrices
    );
  }

  public SessionSectionPricesResponse getSectionPrices(
      Long userId,
      UUID eventId,
      UUID sessionId
  ) {

    // 주최자 확인
    Organizer organizer = organizerRepository.findByUserId(userId)
        .orElseThrow(() ->
            new BusinessException(
                OrganizerErrorCode.ORGANIZER_NOT_FOUND
            )
        );

    organizer.validateActive();

    // 공연 확인
    Event event = eventRepository
        .findByIdAndOrganizerId(eventId, organizer.getId())
        .orElseThrow(() ->
            new BusinessException(
                EventErrorCode.EVENT_NOT_FOUND
            )
        );

    // 공연에 속한 회차 검증
    EventSession eventSession = eventSessionRepository
        .findByIdAndEventId(sessionId, event.getId())
        .orElseThrow(() ->
            new BusinessException(
                EventErrorCode.EVENT_SESSION_NOT_FOUND
            )
        );

    List<SessionSectionPrice> sectionPrices =
        sessionSectionPriceRepository
            .findAllByEventSessionId(sessionId);

    return SessionSectionPricesResponse.from(
        eventSession.getId(),
        sectionPrices
    );
  }
  private void validateDuplicateSections(
      SessionSectionPricesCreateRequest request) {

    Set<UUID> sectionIds = request.sectionPrices().stream()
        .map(SectionPriceRequest::venueSectionId)
        .collect(Collectors.toSet());

    if (sectionIds.size() != request.sectionPrices().size()) {
      throw new BusinessException(EventErrorCode.DUPLICATE_SECTION_PRICE);
    }
  }

  private Map<UUID, VenueSection> loadAndValidateVenueSections(
      Event event,
      SessionSectionPricesCreateRequest request
  ) {

    Set<UUID> sectionIds = request.sectionPrices().stream()
        .map(SectionPriceRequest::venueSectionId)
        .collect(Collectors.toSet());

    List<VenueSection> venueSections =
        venueSectionRepository.findAllById(sectionIds);

    // 요청 공연장 구역과 맞는지 확인
    if (venueSections.size() != sectionIds.size()) {
      throw new BusinessException(VenueErrorCode.VENUE_SECTION_NOT_FOUND);
    }

    for (VenueSection section : venueSections) {
      // 공연의 공연장과 공연장 구역의 공연장이 맞는지 검증
      boolean isEventVenueId = section.getVenue().getId()
          .equals(event.getVenue().getId());

      if (!isEventVenueId) {
        throw new BusinessException(VenueErrorCode.VENUE_SECTION_MISMATCH);
      }

      if (!section.isActive()) {
        throw new BusinessException(VenueErrorCode.INACTIVE_VENUE_SECTION);
      }
    }

    return venueSections.stream()
        .collect(Collectors.toMap(
            VenueSection::getId,
            section -> section
        ));
  }

  private SessionSectionPrice createSectionPrice(
      EventSession eventSession,
      VenueSection venueSection,
      SectionPriceRequest request
  ) {

    return SessionSectionPrice.create(
        eventSession,
        venueSection,
        request.seatGrade(),
        request.priceAmount(),
        request.salesEnabled()
    );

  }


}
