package com.tikitaka.paymentnotification.notification.infrastructure.adapter;

import com.tikitaka.paymentnotification.notification.domain.entity.NotificationInbox;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationInboxRepositoryPort;
import com.tikitaka.paymentnotification.notification.infrastructure.repository.NotificationInboxJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class NotificationInboxRepositoryAdapter implements NotificationInboxRepositoryPort {

    private final NotificationInboxJpaRepository notificationInboxJpaRepository;

    public NotificationInboxRepositoryAdapter(NotificationInboxJpaRepository notificationInboxJpaRepository) {
        this.notificationInboxJpaRepository = notificationInboxJpaRepository;
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return notificationInboxJpaRepository.existsById(eventId);
    }

    @Override
    public NotificationInbox save(NotificationInbox notificationInbox) {
        return notificationInboxJpaRepository.save(notificationInbox);
    }
}
