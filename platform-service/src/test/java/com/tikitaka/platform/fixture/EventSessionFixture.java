package com.tikitaka.platform.fixture;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.event.domain.EventSession;

import java.time.OffsetDateTime;

public class EventSessionFixture {

  private EventSessionFixture() {

  }

  public static EventSession createEventSession(Event event) {
    OffsetDateTime now = OffsetDateTime.now();

    return EventSession.create(
        event,
        1,
        now.plusDays(10),
        now.plusDays(10).plusHours(2),
        now.plusDays(1),
        now.plusDays(9),
        true
    );
  }
}
