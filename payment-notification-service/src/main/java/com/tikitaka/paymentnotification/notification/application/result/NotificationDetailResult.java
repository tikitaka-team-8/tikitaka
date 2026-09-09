package com.tikitaka.paymentnotification.notification.application.result;

import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class NotificationDetailResult {

    private final UUID notificationId;
    private final Long userId;
    private final UUID reservationId;
    private final String reservationNumber;
    private final NotificationType notificationType;
    private final String title;
    private final String content;
    private final NotificationReadStatus readStatus;
    private final Instant lastViewedAt;
    private final Instant createdAt;

    public NotificationDetailResult(Notification notification) {
        this.notificationId = notification.getNotificationId();
        this.userId = notification.getUserId();
        this.reservationId = notification.getReservationId();
        this.reservationNumber = notification.getReservationNumber();
        this.notificationType = notification.getNotificationType();
        this.title = notification.getTitle();
        this.content = notification.getContent();
        this.readStatus = notification.getReadStatus();
        this.lastViewedAt = notification.getLastViewedAt();
        this.createdAt = notification.getCreatedAt();
    }
}
