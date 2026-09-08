package com.tikitaka.paymentnotification.notification.domain.port;

import com.tikitaka.paymentnotification.notification.domain.entity.Notification;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findByIdAndUserId(UUID notificationId, Long userId);
}
