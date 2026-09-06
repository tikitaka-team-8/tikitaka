package com.tikitaka.platform.event.domain;

import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.venue.domain.Venue;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


class EventSessionTest {

  @Test
  void 공연_회차를_생성하면_SCHEDULED_상태가_된다() {
    EventSession eventSession = createEventSession();

    assertThat(eventSession.getStatus())
        .isEqualTo(EventSessionStatus.SCHEDULED);
  }


  @Test
  void SCHEDULED_상태의_공연_회차를_수정할_수_있다() {
    EventSession eventSession = createEventSession();

    OffsetDateTime changedPerformanceStartAt =
        OffsetDateTime.parse("2026-09-11T19:00:00+09:00");

    OffsetDateTime changedPerformanceEndAt =
        OffsetDateTime.parse("2026-09-11T21:00:00+09:00");

    OffsetDateTime changedSalesCloseAt =
        OffsetDateTime.parse("2026-09-11T17:00:00+09:00");

    eventSession.update(
        2,
        changedPerformanceStartAt,
        changedPerformanceEndAt,
        null,
        changedSalesCloseAt,
        false
    );

    assertThat(eventSession.getSessionNumber()).isEqualTo(2);
    assertThat(eventSession.getPerformanceStartAt())
        .isEqualTo(changedPerformanceStartAt);
    assertThat(eventSession.getPerformanceEndAt())
        .isEqualTo(changedPerformanceEndAt);
    assertThat(eventSession.getSalesCloseAt())
        .isEqualTo(changedSalesCloseAt);
    assertThat(eventSession.isQueueEnabled())
        .isFalse();
  }

  private EventSession createEventSession() {
    return EventSession.create(
        createEvent(),
        1,
        OffsetDateTime.parse("2026-09-10T19:00:00+09:00"),
        OffsetDateTime.parse("2026-09-10T21:00:00+09:00"),
        OffsetDateTime.parse("2026-09-01T10:00:00+09:00"),
        OffsetDateTime.parse("2026-09-10T17:00:00+09:00"),
        true
    );
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