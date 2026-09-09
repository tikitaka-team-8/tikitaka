package com.tikitaka.paymentnotification.notification.infrastructure.repository;

import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByNotificationIdAndUserId(UUID notificationId, Long userId);

    @Query("""
            SELECT n
            FROM Notification n
            WHERE (:ownerUserId IS NULL OR n.userId = :ownerUserId)
              AND (:notificationType IS NULL OR n.notificationType = :notificationType)
              AND (:readStatus IS NULL OR n.readStatus = :readStatus)
            """)
    Page<Notification> searchNotifications(Long ownerUserId, NotificationType notificationType, NotificationReadStatus readStatus, Pageable pageable);
}
