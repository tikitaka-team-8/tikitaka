package com.tikitaka.platform.organizer.application;

import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.application.command.OrganizerCreateCommand;
import com.tikitaka.platform.organizer.application.command.OrganizerUpdateCommand;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.domain.OrganizerStatus;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerDetailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;


@ExtendWith(MockitoExtension.class)
class OrganizerServiceTest {

  @Mock
  private OrganizerRepository organizerRepository;

  @InjectMocks
  private OrganizerService organizerService;

  @Test
  void 주최자가_없는_사용자는_등록할_수_있다() {
    Long userId = 1L;

    OrganizerCreateCommand command = createOrganizerCommand(userId);

    given(organizerRepository.existsByUserId(userId))
        .willReturn(false);

    given(organizerRepository.save(any(Organizer.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    organizerService.createOrganizer(command);

    then(organizerRepository)
        .should()
        .save(any(Organizer.class));
  }

  @Test
  void 이미_주최자가_있는_사용자가_등록을_신청하면_예외가_발생한다() {

    Long userId = 1L;

    OrganizerCreateCommand command = createOrganizerCommand(userId);

    given(organizerRepository.existsByUserId(userId))
        .willReturn(true);

    BusinessException e = catchThrowableOfType(() ->
            organizerService.createOrganizer(command),
        BusinessException.class
    );

    assertThat(e.getErrorCode())
        .isEqualTo(OrganizerErrorCode.ORGANIZER_ALREADY_EXISTS);
  }

  @Test
  void 내_주최자_정보를_조회() {

    Long userId = 1L;

    Organizer organizer = createOrganizer(userId);

    given(organizerRepository.findByUserId(userId))
        .willReturn(Optional.of(organizer));

    OrganizerDetailResponse response = organizerService.getMyOrganizer(userId);

    assertThat(response.organizerId()).isEqualTo(organizer.getId());
    assertThat(response.name()).isEqualTo(organizer.getName());
    assertThat(response.status()).isEqualTo(organizer.getStatus());
    assertThat(response.approvedAt()).isEqualTo(organizer.getApprovedAt());
  }

  @Test
  void 주최자_정보가_없으면_예외가_발생() {
    Long userId = 1L;

    given(organizerRepository.findByUserId(userId))
        .willReturn(Optional.empty());

    BusinessException ex = catchThrowableOfType(() ->
        organizerService.getMyOrganizer(userId),
        BusinessException.class
    );

    assertThat(ex.getErrorCode()).isEqualTo(OrganizerErrorCode.ORGANIZER_NOT_FOUND);
  }

  @Test
  void 주최자_정보_부분_수정() {

    Long userId = 1L;

    Organizer organizer = createOrganizer(userId);

    OrganizerUpdateCommand command = new OrganizerUpdateCommand(
        userId,
        null,
        null,
        "update@tikitaka.com",
        "010-9876-5434",
        "수정"
    );

    given(organizerRepository.findByUserId(userId))
        .willReturn(Optional.of(organizer));

    OrganizerDetailResponse response =
        organizerService.updateOrganizer(command);

    assertThat(response.organizerId()).isEqualTo(organizer.getId());
    assertThat(response.contactEmail()).isEqualTo(organizer.getContactEmail());
    assertThat(response.contactPhone()).isEqualTo(organizer.getContactPhone());
    assertThat(response.description()).isEqualTo(organizer.getDescription());
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