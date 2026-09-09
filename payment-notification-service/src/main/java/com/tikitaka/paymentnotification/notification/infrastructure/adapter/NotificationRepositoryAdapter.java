package com.tikitaka.paymentnotification.notification.infrastructure.adapter;

import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationRepositoryPort;
import com.tikitaka.paymentnotification.notification.infrastructure.repository.NotificationJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final NotificationJpaRepository notificationJpaRepository;

    public NotificationRepositoryAdapter(NotificationJpaRepository notificationJpaRepository) {
        this.notificationJpaRepository = notificationJpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(UUID notificationId) {
        return notificationJpaRepository.findById(notificationId);
    }

    @Override
    public Optional<Notification> findByIdAndUserId(UUID notificationId, Long userId) {
        return notificationJpaRepository.findByNotificationIdAndUserId(notificationId, userId);
    }

    @Override
    public Page<Notification> searchNotifications(Long ownerUserId, NotificationType notificationType, NotificationReadStatus readStatus, Pageable pageable) {
        return notificationJpaRepository.searchNotifications(ownerUserId, notificationType, readStatus, pageable);
    }
}
