package com.tikitaka.paymentnotification.notification.infrastructure.repository;

import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByNotificationIdAndUserId(UUID notificationId, Long userId);
}
