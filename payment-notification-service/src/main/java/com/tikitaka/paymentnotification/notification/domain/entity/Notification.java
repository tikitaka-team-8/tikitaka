package com.tikitaka.paymentnotification.notification.domain.entity;

import com.tikitaka.paymentnotification.global.persistence.entity.BaseEntity;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID notificationId;

    @Column(nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private UUID reservationId;

    @Column(nullable = false, length = 30, updatable = false)
    private String reservationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private NotificationType notificationType;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationReadStatus readStatus;

    private Instant lastViewedAt;

    private Notification(Long createdBy) {
        super(createdBy);
    }

    public static Notification create(UUID sourceEventId, Long userId, UUID reservationId, String reservationNumber,
                                      NotificationType notificationType, String title, String content, Long createdBy) {

        Notification notification = new Notification(createdBy);
        notification.sourceEventId = sourceEventId;
        notification.userId = userId;
        notification.reservationId = reservationId;
        notification.reservationNumber = reservationNumber;
        notification.notificationType = notificationType;
        notification.title = title;
        notification.content = content;
        notification.readStatus = NotificationReadStatus.UNREAD;

        return notification;
    }

    public void markAsRead(Long updatedBy, Instant lastViewedAt) {
        if (lastViewedAt == null) {
            throw new IllegalArgumentException("읽음 처리 시각은 필수입니다.");
        }

        this.readStatus = NotificationReadStatus.READ;
        this.lastViewedAt = lastViewedAt;
        markAsUpdated(updatedBy);
    }
}
