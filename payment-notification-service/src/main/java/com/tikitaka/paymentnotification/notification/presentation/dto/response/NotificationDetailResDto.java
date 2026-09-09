package com.tikitaka.paymentnotification.notification.presentation.dto.response;

import com.tikitaka.paymentnotification.notification.application.result.NotificationDetailResult;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class NotificationDetailResDto {

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

    public NotificationDetailResDto(NotificationDetailResult result) {
        this.notificationId = result.getNotificationId();
        this.userId = result.getUserId();
        this.reservationId = result.getReservationId();
        this.reservationNumber = result.getReservationNumber();
        this.notificationType = result.getNotificationType();
        this.title = result.getTitle();
        this.content = result.getContent();
        this.readStatus = result.getReadStatus();
        this.lastViewedAt = result.getLastViewedAt();
        this.createdAt = result.getCreatedAt();
    }
}
