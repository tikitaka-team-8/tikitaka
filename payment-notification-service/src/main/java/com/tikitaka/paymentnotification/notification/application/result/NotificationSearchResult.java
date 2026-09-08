package com.tikitaka.paymentnotification.notification.application.result;

import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class NotificationSearchResult {

    private final UUID notificationId;
    private final Long userId;
    private final NotificationType notificationType;
    private final String title;
    private final String content;
    private final NotificationReadStatus readStatus;
    private final Instant createdAt;

    public NotificationSearchResult(Notification notification) {
        this.notificationId = notification.getNotificationId();
        this.userId = notification.getUserId();
        this.notificationType = notification.getNotificationType();
        this.title = notification.getTitle();
        this.content = notification.getContent();
        this.readStatus = notification.getReadStatus();
        this.createdAt = notification.getCreatedAt();
    }
}
