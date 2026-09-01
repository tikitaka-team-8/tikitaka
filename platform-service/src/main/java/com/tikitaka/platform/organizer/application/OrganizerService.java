package com.tikitaka.platform.organizer.application;

import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.application.command.OrganizerCreateCommand;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizerService {

  private final OrganizerRepository organizerRepository;

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

  private void validateDuplicateOrganizer(Long userId) {
    if (organizerRepository.existsByUserId(userId)) {
      throw new BusinessException(OrganizerErrorCode.ORGANIZER_ALREADY_EXISTS);
    }
  }
}
