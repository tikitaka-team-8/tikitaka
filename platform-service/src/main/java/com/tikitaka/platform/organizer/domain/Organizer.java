package com.tikitaka.platform.organizer.domain;

import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.organizer.exception.OrganizerErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_organizer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organizer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "representative_name", nullable = false, length = 100)
  private String representativeName;

  @Column(name = "contact_email", nullable = false, length = 100)
  private String contactEmail;

  @Column(name = "contact_phone", length = 20)
  private String contactPhone;

  @Column(name = "description")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrganizerStatus status;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  private Organizer(
      Long userId,
      String name,
      String representativeName,
      String contactEmail,
      String contactPhone,
      String description
  ) {

    this.userId = userId;
    this.name = name;
    this.representativeName = representativeName;
    this.contactEmail = contactEmail;
    this.contactPhone = contactPhone;
    this.description = description;
    this.status = OrganizerStatus.PENDING;
  }

  public static Organizer create(
      Long userId,
      String name,
      String representativeName,
      String contactEmail,
      String contactPhone,
      String description
  ) {
    return new Organizer(
        userId,
        name,
        representativeName,
        contactEmail,
        contactPhone,
        description
    );
  }

  // 상태 변경
  public void changeStatus(
      OrganizerStatus targetStatus,
      OffsetDateTime approvedAt
  ) {

    if (this.status == targetStatus) {
      return;
    }

    // 허용되는 상태 변경 검증
    validateTransition(targetStatus);

    // 승신 시간 기록
    if (this.status == OrganizerStatus.PENDING
        && targetStatus == OrganizerStatus.ACTIVE
        && this.approvedAt == null) {

      this.approvedAt = approvedAt;
    }

    this.status = targetStatus;
  }

  private void validateTransition(OrganizerStatus targetStatus) {
    boolean allowed = switch (this.status) {
      case PENDING -> targetStatus == OrganizerStatus.ACTIVE
          || targetStatus == OrganizerStatus.REJECTED;

      case ACTIVE -> targetStatus == OrganizerStatus.SUSPENDED;
      case SUSPENDED -> targetStatus == OrganizerStatus.ACTIVE;
      case REJECTED -> false;
    };

    if (!allowed) {
      throw new BusinessException(OrganizerErrorCode.INVALID_STATUS_TRANSITION);
    }
  }
}
