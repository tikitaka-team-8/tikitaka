package com.tikitaka.platform.event.domain;

import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.venue.domain.Venue;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EventTest {

  @Test
  void 공연을_생성하면_DRAFT_상태가_된다() {
    Event event = createEvent();

    assertThat(event.getStatus())
        .isEqualTo(EventStatus.DRAFT);
  }

  @Test
  void DRAFT_상태의_공연을_공개하면_UPCOMING_상태가_된다() {
    Event event = createEvent();

    event.publish();

    assertThat(event.getStatus())
        .isEqualTo(EventStatus.UPCOMING);
  }

  private Event createEvent() {
    return Event.create(
        mock(Organizer.class),
        mock(Venue.class),
        "뮤지컬 티키타카",
        "설명",
        120
    );
  }
}