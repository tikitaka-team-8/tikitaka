package com.tikitaka.platform.organizer.application;

import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.application.command.OrganizerCreateCommand;
import com.tikitaka.platform.organizer.application.command.OrganizerUpdateCommand;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerCreateResponse;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizerService {

  private final OrganizerRepository organizerRepository;


  // 주최자 신청
  @Transactional
  public OrganizerCreateResponse createOrganizer(
      OrganizerCreateCommand command
  ) {

    // 중복 검증
    validateDuplicateOrganizer(command.userId());

    Organizer organizer = Organizer.create(
        command.userId(),
        command.name(),
        command.representativeName(),
        command.contactEmail(),
        command.contactPhone(),
        command.description()
    );

    Organizer savedOrganizer = organizerRepository.save(organizer);
    return OrganizerCreateResponse.from(savedOrganizer);
  }

  // 주최자 조회
  public OrganizerDetailResponse getMyOrganizer(Long userId) {
    Organizer organizer = getOrganizerUserId(userId);

    return OrganizerDetailResponse.from(organizer);
  }

  // 주최자 수정
  public OrganizerDetailResponse updateOrganizer(OrganizerUpdateCommand command) {
    Organizer organizer = getOrganizerUserId(command.userId());

    // 수정
    organizer.updateInfo(
        command.name(),
        command.representativeName(),
        command.contactEmail(),
        command.contactPhone(),
        command.description()
    );

    return OrganizerDetailResponse.from(organizer);
  }

  private Organizer getOrganizerUserId(Long userId) {
    return organizerRepository.findByUserId(userId)
        .orElseThrow(() ->
            new BusinessException(OrganizerErrorCode.ORGANIZER_NOT_FOUND)
        );
  }

  private void validateDuplicateOrganizer(Long userId) {
    if (organizerRepository.existsByUserId(userId)) {
      throw new BusinessException(OrganizerErrorCode.ORGANIZER_ALREADY_EXISTS);
    }
  }
}
