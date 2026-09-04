package com.tikitaka.platform.fixture;

import com.tikitaka.platform.organizer.domain.Organizer;

public class OrganizerFixture {

  private OrganizerFixture() {

  }

  public static Organizer createOrganizer(Long userId) {
    return Organizer.create(
        userId,
        "티키타카",
        "키키",
        "organizer@tikitaka.com",
        "010-1234-5677",
        "공연 기획 운영"
    );
  }
}
