package com.tikitaka.paymentnotification.notification.application.service;

import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.global.exception.CommonErrorCode;
import com.tikitaka.paymentnotification.notification.application.command.ReservationConfirmedNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.command.ReservationFailedNotificationCommand;
import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.entity.NotificationInbox;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationInboxRepositoryPort;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationRepositoryPort;
import com.tikitaka.paymentnotification.notification.exception.NotificationErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ReservationNotificationEventService {
    private static final int SUPPORTED_EVENT_VERSION = 1;
    private static final long SYSTEM_USER_ID = 0L;
    private static final String CONFIRMED_TITLE = "[예매완료]";
    private static final String FAILED_TITLE = "[예매 실패]";
    private static final DateTimeFormatter SESSION_START_AT_FORMATTER =
            DateTimeFormatter.
                    ofPattern("yyyy-MM-dd (E) HH:mm", Locale.KOREAN)
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final NotificationRepositoryPort notificationRepositoryPort;
    private final NotificationInboxRepositoryPort notificationInboxRepositoryPort;

    public ReservationNotificationEventService(NotificationRepositoryPort notificationRepositoryPort,
                                               NotificationInboxRepositoryPort notificationInboxRepositoryPort) {

        this.notificationRepositoryPort = notificationRepositoryPort;
        this.notificationInboxRepositoryPort = notificationInboxRepositoryPort;
    }

    public boolean processReservationConfirmed(ReservationConfirmedNotificationCommand command) {

        // 수신한 예매 확정 이벤트의 필수값과 계약 검증
        validateCommonValues(
                command.eventId(), command.eventType(), command.eventVersion(), command.occurredAt(),
                command.reservationId(), command.reservationNumber(), command.userId(), command.eventTitle(),
                command.sessionStartAt(), NotificationType.RESERVATION_CONFIRMED
        );

        // 이미 처리한 이벤트는 알림을 중복 생성하지 않고 정상 종료
        if (notificationInboxRepositoryPort.existsByEventId(command.eventId())) {
            return false;
        }

        // 예매 확정 이벤트의 사용자 알림 내용 구성
        String content = createConfirmedContent(
                command.reservationNumber(), command.eventTitle(), command.sessionStartAt()
        );

        // 알림과 처리 완료 Inbox를 같은 트랜잭션으로 저장
        saveNotificationAndInbox(
                command.eventId(), command.userId(), command.reservationId(), command.reservationNumber(),
                NotificationType.RESERVATION_CONFIRMED, CONFIRMED_TITLE, content
        );
        return true;
    }

    public boolean processReservationFailed(ReservationFailedNotificationCommand command) {

        // 수신한 예매 실패 이벤트의 필수값과 계약 검증
        validateCommonValues(
                command.eventId(), command.eventType(), command.eventVersion(), command.occurredAt(),
                command.reservationId(), command.reservationNumber(), command.userId(), command.eventTitle(),
                command.sessionStartAt(), NotificationType.RESERVATION_FAILED
        );

        // 이미 처리한 이벤트는 알림을 중복 생성하지 않고 정상 종료
        if (notificationInboxRepositoryPort.existsByEventId(command.eventId())) {
            return false;
        }

        String failureReason = convertFailureReason(command.failureReason());

        // 예매 실패 이벤트의 사용자 알림 내용 구성
        String content = createFailedContent(command.eventTitle(), failureReason);

        // 알림과 처리 완료 Inbox를 같은 트랜잭션으로 저장
        saveNotificationAndInbox(
                command.eventId(), command.userId(), command.reservationId(), command.reservationNumber(),
                NotificationType.RESERVATION_FAILED, FAILED_TITLE, content
        );
        return true;
    }

    private void validateCommonValues(UUID eventId, String eventType, Integer eventVersion, Instant occurredAt,
                                      UUID reservationId, String reservationNumber, Long userId, String eventTitle,
                                      Instant sessionStartAt, NotificationType expectedEventType) {

        if (eventId == null || eventType == null || eventType.isBlank() || eventVersion == null || occurredAt == null
                || reservationId == null || reservationNumber == null || reservationNumber.isBlank()
                || userId == null || userId <= 0 || eventTitle == null || eventTitle.isBlank()
                || sessionStartAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (!expectedEventType.name().equals(eventType)) {
            throw new BusinessException(NotificationErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
        }
        if (eventVersion != SUPPORTED_EVENT_VERSION) {
            throw new BusinessException(CommonErrorCode.UNSUPPORTED_REQUEST);
        }
    }

    private String createConfirmedContent(String reservationNumber, String eventTitle, Instant sessionStartAt) {
        return "고객님, 예매가 완료되었습니다.\n"
                + "예매번호: " + reservationNumber + "\n"
                + "공연: " + eventTitle + "\n"
                + "일시: " + SESSION_START_AT_FORMATTER.format(sessionStartAt);
    }

    private String createFailedContent(String eventTitle, String failureReason) {
        return "고객님, 예매가 실패하였습니다.\n"
                + "공연: " + eventTitle + "\n"
                + "사유: " + failureReason;
    }

    private String convertFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return switch (failureReason) {
            case "SEAT_HOLD_EXPIRED" -> "좌석 선점 시간이 만료되었습니다.";
            case "PAYMENT_TIMEOUT" -> "결제 시간이 초과되었습니다.";
            case "PAYMENT_FAILED" -> "결제에 실패하였습니다.";
            default -> throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        };
    }

    private void saveNotificationAndInbox(UUID eventId, Long userId, UUID reservationId, String reservationNumber, NotificationType notificationType, String title, String content) {

        Notification notification = Notification.create(
                eventId, userId, reservationId, reservationNumber, notificationType, title, content, SYSTEM_USER_ID
        );
        NotificationInbox notificationInbox = NotificationInbox.create(eventId, reservationId, notificationType);

        notificationRepositoryPort.save(notification);
        notificationInboxRepositoryPort.save(notificationInbox);
    }
}
