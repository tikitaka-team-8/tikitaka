package com.tikitaka.platform.fixture;

import com.tikitaka.platform.venue.domain.Venue;
import com.tikitaka.platform.venue.domain.VenueSection;

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

  public static Venue deactivateVenue() {
    Venue venue = createVenue();
    venue.deactivate();
    return venue;
  }

  public static VenueSection createVenueSection() {
    return VenueSection.create(
        createVenue(),
        "VIP",
        "1층",
        1,
        true
    );
  }

}
