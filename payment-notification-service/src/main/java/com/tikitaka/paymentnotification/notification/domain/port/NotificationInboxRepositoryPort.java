package com.tikitaka.paymentnotification.notification.domain.port;

import com.tikitaka.paymentnotification.notification.domain.entity.NotificationInbox;

import java.util.UUID;

public interface NotificationInboxRepositoryPort {

    boolean existsByEventId(UUID eventId);

    NotificationInbox save(NotificationInbox notificationInbox);
}
