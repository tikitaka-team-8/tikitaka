package com.tikitaka.platform.organizer.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
class OrganizerTest {


  @Test
  void 주최자를_생성하면_PENDING_상태가_된다() {

    Organizer organizer = createPendingOrganizer();

    assertThat(organizer.getStatus()).isEqualTo(OrganizerStatus.PENDING);
    assertThat(organizer.getApprovedAt()).isNull();
  }

  @Test
  void PENDING_주최자를_승인하면_ACTIVE가_된다() {

    Organizer organizer = createPendingOrganizer();
    OffsetDateTime approvedAt = OffsetDateTime.now();

    organizer.changeStatus(OrganizerStatus.ACTIVE, approvedAt);

    assertThat(organizer.getStatus()).isEqualTo(OrganizerStatus.ACTIVE);
    assertThat(organizer.getApprovedAt()).isEqualTo(approvedAt);
  }

  private Organizer createPendingOrganizer() {
    return Organizer.create(
        1L,
        "티키타카",
        "키키",
        "organizer@test.com",
        "010-1234-5678",
        "공연 기획사"
    );
  }
}