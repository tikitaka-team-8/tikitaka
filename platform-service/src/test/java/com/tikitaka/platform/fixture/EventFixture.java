package com.tikitaka.platform.fixture;

import com.tikitaka.platform.event.domain.Event;
import com.tikitaka.platform.organizer.domain.Organizer;
import com.tikitaka.platform.venue.domain.Venue;

public class EventFixture {

  private EventFixture(){

  }

  public static Event createPublicEvent(Organizer organizer, Venue venue) {
    Event event = Event.create(
        organizer,
        venue,
        "티키타카 콘서트",
        "설명",
        180
    );

    event.publish();
    return event;
  }

  public static Event createEvent(Organizer organizer, Venue venue) {
    return Event.create(
        organizer,
        venue,
        "티키타카 콘서트",
        "설명",
        180
    );
  }
}
