package com.tikitaka.platform.venue.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_venue")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "postal_code", nullable = false, length = 10)
  private String postalCode;

  @Column(name = "address", nullable = false, length = 255)
  private String address;

  @Column(name = "address_detail", length = 255)
  private String addressDetail;

  @Column(name = "contact_phone", length = 20)
  private String contactPhone;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  private Venue(
      String name,
      String postalCode,
      String address,
      String addressDetail,
      String contactPhone
  ) {

    this.name = name;
    this.postalCode = postalCode;
    this.address = address;
    this.addressDetail = addressDetail;
    this.contactPhone = contactPhone;
  }

  public static Venue create(
      String name,
      String postalCode,
      String address,
      String addressDetail,
      String contactPhone
  ) {
    return new Venue(
        name,
        postalCode,
        address,
        addressDetail,
        contactPhone
    );
  }
}
