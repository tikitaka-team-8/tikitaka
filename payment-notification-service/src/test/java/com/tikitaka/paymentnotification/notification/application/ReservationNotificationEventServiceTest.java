package com.tikitaka.paymentnotification.notification.application;

import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.global.exception.CommonErrorCode;
import com.tikitaka.paymentnotification.notification.application.command.ReservationConfirmedNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.command.ReservationFailedNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.service.ReservationNotificationEventService;
import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.entity.NotificationInbox;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationInboxRepositoryPort;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationNotificationEventServiceTest {

    @Mock
    private NotificationRepositoryPort notificationRepositoryPort;

    @Mock
    private NotificationInboxRepositoryPort notificationInboxRepositoryPort;

    @InjectMocks
    private ReservationNotificationEventService reservationNotificationEventService;

    @Test
    void 예매_확정_이벤트를_처리하면_읽지_않은_알림과_Inbox를_저장한다() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        ReservationConfirmedNotificationCommand command = new ReservationConfirmedNotificationCommand(
                eventId, "RESERVATION_CONFIRMED", 1, Instant.parse("2026-09-08T01:00:00Z"),
                reservationId, "RSV-260908-123456789012", 1L, "티키타카 콘서트",
                Instant.parse("2026-09-13T07:30:00Z")
        );

        // when
        boolean result = reservationNotificationEventService.processReservationConfirmed(command);

        // then
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        ArgumentCaptor<NotificationInbox> inboxCaptor = ArgumentCaptor.forClass(NotificationInbox.class);
        verify(notificationRepositoryPort).save(notificationCaptor.capture());
        verify(notificationInboxRepositoryPort).save(inboxCaptor.capture());

        Notification notification = notificationCaptor.getValue();
        assertThat(result).isTrue();
        assertThat(notification.getSourceEventId()).isEqualTo(eventId);
        assertThat(notification.getUserId()).isEqualTo(1L);
        assertThat(notification.getReservationId()).isEqualTo(reservationId);
        assertThat(notification.getReservationNumber()).isEqualTo("RSV-260908-123456789012");
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
        assertThat(notification.getTitle()).isEqualTo("[예매완료]");
        assertThat(notification.getContent()).isEqualTo(
                "고객님, 예매가 완료되었습니다.\n"
                        + "예매번호: RSV-260908-123456789012\n"
                        + "공연: 티키타카 콘서트\n"
                        + "일시: 2026-09-13 (일) 16:30"
        );
        assertThat(notification.getReadStatus()).isEqualTo(NotificationReadStatus.UNREAD);
        assertThat(notification.getLastViewedAt()).isNull();

        NotificationInbox inbox = inboxCaptor.getValue();
        assertThat(inbox.getEventId()).isEqualTo(eventId);
        assertThat(inbox.getReservationId()).isEqualTo(reservationId);
        assertThat(inbox.getEventType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
    }

    @Test
    void 예매_실패_이벤트를_처리하면_실패_사유를_변환한_알림과_Inbox를_저장한다() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        ReservationFailedNotificationCommand command = new ReservationFailedNotificationCommand(
                eventId, "RESERVATION_FAILED", 1, Instant.parse("2026-09-08T01:00:00Z"),
                reservationId, "RSV-260908-123456789012", 1L, "티키타카 콘서트",
                Instant.parse("2026-09-13T07:30:00Z"), "PAYMENT_FAILED"
        );

        // when
        boolean result = reservationNotificationEventService.processReservationFailed(command);

        // then
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        ArgumentCaptor<NotificationInbox> inboxCaptor = ArgumentCaptor.forClass(NotificationInbox.class);
        verify(notificationRepositoryPort).save(notificationCaptor.capture());
        verify(notificationInboxRepositoryPort).save(inboxCaptor.capture());

        Notification notification = notificationCaptor.getValue();
        assertThat(result).isTrue();
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.RESERVATION_FAILED);
        assertThat(notification.getReservationNumber()).isEqualTo("RSV-260908-123456789012");
        assertThat(notification.getTitle()).isEqualTo("[예매 실패]");
        assertThat(notification.getContent()).isEqualTo(
                "고객님, 예매가 실패하였습니다.\n"
                        + "공연: 티키타카 콘서트\n"
                        + "사유: 결제에 실패하였습니다."
        );
        assertThat(notification.getReadStatus()).isEqualTo(NotificationReadStatus.UNREAD);
        assertThat(notification.getLastViewedAt()).isNull();
        assertThat(inboxCaptor.getValue().getEventType()).isEqualTo(NotificationType.RESERVATION_FAILED);
    }

    @Test
    void 이미_처리한_이벤트이면_알림과_Inbox를_다시_저장하지_않는다() {
        // given
        UUID eventId = UUID.randomUUID();
        ReservationConfirmedNotificationCommand command = new ReservationConfirmedNotificationCommand(
                eventId, "RESERVATION_CONFIRMED", 1, Instant.parse("2026-09-08T01:00:00Z"),
                UUID.randomUUID(), "RSV-260908-123456789012", 1L, "티키타카 콘서트",
                Instant.parse("2026-09-13T07:30:00Z")
        );
        when(notificationInboxRepositoryPort.existsByEventId(eventId)).thenReturn(true);

        // when
        boolean result = reservationNotificationEventService.processReservationConfirmed(command);

        // then
        assertThat(result).isFalse();
        verify(notificationRepositoryPort, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationInboxRepositoryPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 지원하지_않는_실패_사유이면_알림을_저장하지_않는다() {
        // given
        ReservationFailedNotificationCommand command = new ReservationFailedNotificationCommand(
                UUID.randomUUID(), "RESERVATION_FAILED", 1, Instant.parse("2026-09-08T01:00:00Z"),
                UUID.randomUUID(), "RSV-260908-123456789012", 1L, "티키타카 콘서트",
                Instant.parse("2026-09-13T07:30:00Z"), "UNKNOWN_REASON"
        );

        // when
        Throwable exception = catchThrowable(
                () -> reservationNotificationEventService.processReservationFailed(command)
        );

        // then
        assertThat(exception)
                .isInstanceOfSatisfying(BusinessException.class,
                        businessException -> assertThat(businessException.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
        verify(notificationRepositoryPort, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationInboxRepositoryPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void Inbox_저장에_실패하면_예외를_호출자에게_전파한다() {
        // given
        ReservationConfirmedNotificationCommand command = new ReservationConfirmedNotificationCommand(
                UUID.randomUUID(), "RESERVATION_CONFIRMED", 1, Instant.parse("2026-09-08T01:00:00Z"),
                UUID.randomUUID(), "RSV-260908-123456789012", 1L, "티키타카 콘서트",
                Instant.parse("2026-09-13T07:30:00Z")
        );
        when(notificationInboxRepositoryPort.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("Inbox 저장 실패"));

        // when
        Throwable exception = catchThrowable(
                () -> reservationNotificationEventService.processReservationConfirmed(command)
        );

        // then
        assertThat(exception)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Inbox 저장 실패");
        verify(notificationRepositoryPort).save(org.mockito.ArgumentMatchers.any());
    }
}
