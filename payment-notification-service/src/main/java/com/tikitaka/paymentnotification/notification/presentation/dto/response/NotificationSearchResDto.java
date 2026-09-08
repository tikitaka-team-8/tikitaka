package com.tikitaka.paymentnotification.notification.presentation.dto.response;

import com.tikitaka.paymentnotification.notification.application.result.NotificationSearchResult;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class NotificationSearchResDto {

    private final UUID notificationId;
    private final Long userId;
    private final NotificationType notificationType;
    private final String title;
    private final String content;
    private final NotificationReadStatus readStatus;
    private final Instant createdAt;

    public NotificationSearchResDto(NotificationSearchResult result) {
        this.notificationId = result.getNotificationId();
        this.userId = result.getUserId();
        this.notificationType = result.getNotificationType();
        this.title = result.getTitle();
        this.content = result.getContent();
        this.readStatus = result.getReadStatus();
        this.createdAt = result.getCreatedAt();
    }
}
