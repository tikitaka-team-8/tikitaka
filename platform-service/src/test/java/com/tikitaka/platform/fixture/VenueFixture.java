package com.tikitaka.platform.fixture;

import com.tikitaka.platform.venue.domain.Venue;

public class VenueFixture {

  private VenueFixture() {
  }

  public static Venue createVenue() {
    return Venue.create(
        "티키타카 공연장",
        "25812",
        "서울특별시 티키타가",
        "티키타카호",
        "010-1234-5678"
    );
  }
}
