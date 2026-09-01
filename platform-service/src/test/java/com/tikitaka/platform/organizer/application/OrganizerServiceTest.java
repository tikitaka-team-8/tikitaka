package com.tikitaka.platform.organizer.application;

import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.application.command.OrganizerCreateCommand;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import com.tikitaka.platform.organizer.infrastructure.OrganizerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
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