package com.tikitaka.paymentnotification.notification.domain.entity;

import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_notification_inbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationInbox {

    @Id
    @Column(updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100, updatable = false)
    private NotificationType eventType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    private NotificationInbox(UUID eventId, UUID reservationId, NotificationType eventType) {
        this.eventId = eventId;
        this.reservationId = reservationId;
        this.eventType = eventType;
    }

    public static NotificationInbox create(UUID eventId, UUID reservationId, NotificationType eventType) {
        return new NotificationInbox(eventId, reservationId, eventType);
    }
}
