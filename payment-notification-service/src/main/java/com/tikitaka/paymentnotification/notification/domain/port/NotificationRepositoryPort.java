package com.tikitaka.paymentnotification.notification.domain.port;

import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findByIdAndUserId(UUID notificationId, Long userId);

    Page<Notification> searchNotifications(Long ownerUserId, NotificationType notificationType, NotificationReadStatus readStatus, Pageable pageable);
}
