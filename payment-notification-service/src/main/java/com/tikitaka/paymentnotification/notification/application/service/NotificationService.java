package com.tikitaka.paymentnotification.notification.application.service;

import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.global.exception.CommonErrorCode;
import com.tikitaka.paymentnotification.notification.application.command.ReadNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.command.SearchNotificationsCommand;
import com.tikitaka.paymentnotification.notification.application.result.NotificationDetailResult;
import com.tikitaka.paymentnotification.notification.application.result.NotificationSearchResult;
import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationRepositoryPort;
import com.tikitaka.paymentnotification.notification.exception.NotificationErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private static final String USER_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt");

    private final NotificationRepositoryPort notificationRepositoryPort;

    public NotificationService(NotificationRepositoryPort notificationRepositoryPort) {
        this.notificationRepositoryPort = notificationRepositoryPort;
    }

    public Page<NotificationSearchResult> searchNotifications(SearchNotificationsCommand command) {

        // 로그인 역할에 따라 조회 가능한 사용자 범위 결정
        Long ownerUserId = resolveOwnerUserId(command);

        // 요청한 알림 필터와 페이징 조건 검증 및 변환
        NotificationType notificationType = resolveNotificationType(command.getNotificationType());
        NotificationReadStatus readStatus = resolveReadStatus(command.getReadStatus());
        Pageable pageable = normalizePageable(command.getPageable());

        // 조건에 맞는 알림 목록 조회 및 응답 결과 변환
        return notificationRepositoryPort.searchNotifications(ownerUserId, notificationType, readStatus, pageable)
                .map(NotificationSearchResult::new);
    }

    @Transactional
    public NotificationDetailResult readNotification(ReadNotificationCommand command) {

        // 역할에 따라 조회 범위를 제한하여 알림 조회
        Notification notification = findNotification(command);

        // 본인 소유 알림인 경우에만 읽음 상태와 마지막 조회 시각 갱신
        if (Objects.equals(notification.getUserId(), command.getLoginUserId())) {
            notification.markAsRead(command.getLoginUserId(), Instant.now());
        }

        return new NotificationDetailResult(notification);
    }

    private Notification findNotification(ReadNotificationCommand command) {
        if (USER_ROLE.equals(command.getUserRole()) && command.getLoginUserId() != null) {
            return notificationRepositoryPort.findByIdAndUserId(command.getNotificationId(), command.getLoginUserId())
                    .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        }
        if (ADMIN_ROLE.equals(command.getUserRole())) {
            return notificationRepositoryPort.findById(command.getNotificationId())
                    .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    private Long resolveOwnerUserId(SearchNotificationsCommand command) {
        if (ADMIN_ROLE.equals(command.getUserRole())) {
            return null;
        }
        if (USER_ROLE.equals(command.getUserRole()) && command.getLoginUserId() != null) {
            return command.getLoginUserId();
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    private NotificationType resolveNotificationType(String notificationType) {
        if (notificationType == null || notificationType.isBlank()) {
            return null;
        }

        try {
            return NotificationType.valueOf(notificationType);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(NotificationErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
        }
    }

    private NotificationReadStatus resolveReadStatus(String readStatus) {
        if (readStatus == null || readStatus.isBlank()) {
            return null;
        }

        try {
            return NotificationReadStatus.valueOf(readStatus);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_QUERY);
        }
    }

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable == null) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_QUERY);
        }

        int page = Math.max(pageable.getPageNumber(), DEFAULT_PAGE);
        int size = normalizePageSize(pageable.getPageSize());
        List<Sort.Order> sortOrders = pageable.getSort().stream().toList();

        Sort.Order sortOrder;
        if (sortOrders.isEmpty()) {
            sortOrder = Sort.Order.desc("createdAt");
        }
        else {
            if (sortOrders.size() != 1) {
                throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_QUERY);
            }
            sortOrder = sortOrders.get(0);
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortOrder.getProperty())) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_QUERY);
        }

        return PageRequest.of(page, size, Sort.by(sortOrder));
    }

    private int normalizePageSize(int size) {
        return switch (size) {
            case 10, 30, 50 -> size;
            default -> DEFAULT_SIZE;
        };
    }
}
