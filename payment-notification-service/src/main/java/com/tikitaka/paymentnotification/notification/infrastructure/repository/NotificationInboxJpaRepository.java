package com.tikitaka.paymentnotification.notification.infrastructure.repository;

import com.tikitaka.paymentnotification.notification.domain.entity.NotificationInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationInboxJpaRepository extends JpaRepository<NotificationInbox, UUID> {
}
