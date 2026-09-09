package com.tikitaka.paymentnotification.notification.application;

import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.notification.application.command.ReadNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.command.SearchNotificationsCommand;
import com.tikitaka.paymentnotification.notification.application.result.NotificationDetailResult;
import com.tikitaka.paymentnotification.notification.application.result.NotificationSearchResult;
import com.tikitaka.paymentnotification.notification.application.service.NotificationService;
import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import com.tikitaka.paymentnotification.notification.domain.port.NotificationRepositoryPort;
import com.tikitaka.paymentnotification.notification.exception.NotificationErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long OTHER_USER_ID = 3L;
    private static final UUID NOTIFICATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String RESERVATION_NUMBER = "RSV-260908-ABCDEF123456";

    @Mock
    private NotificationRepositoryPort notificationRepositoryPort;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void 사용자는_본인의_알림을_유형과_읽음상태로_조회한다() {
        // given
        Pageable requestedPageable = PageRequest.of(2, 30, Sort.by(Sort.Direction.ASC, "createdAt"));
        SearchNotificationsCommand command = new SearchNotificationsCommand(
                USER_ID, "USER", "RESERVATION_CONFIRMED", "UNREAD", requestedPageable);
        Page<Notification> notificationPage = new PageImpl<>(
                List.of(createNotification()), requestedPageable, 61);

        given(notificationRepositoryPort.searchNotifications(
                eq(USER_ID), eq(NotificationType.RESERVATION_CONFIRMED),
                eq(NotificationReadStatus.UNREAD), any(Pageable.class)
        )).willReturn(notificationPage);

        // when
        Page<NotificationSearchResult> resultPage = notificationService.searchNotifications(command);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepositoryPort).searchNotifications(
                eq(USER_ID), eq(NotificationType.RESERVATION_CONFIRMED),
                eq(NotificationReadStatus.UNREAD), pageableCaptor.capture()
        );
        Pageable normalizedPageable = pageableCaptor.getValue();

        assertThat(normalizedPageable.getPageNumber()).isEqualTo(2);
        assertThat(normalizedPageable.getPageSize()).isEqualTo(30);
        assertThat(normalizedPageable.getSort().getOrderFor("createdAt")).isNotNull().satisfies(order ->
                assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
        assertThat(resultPage.getContent()).singleElement().satisfies(result -> {
            assertThat(result.getNotificationId()).isEqualTo(NOTIFICATION_ID);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getNotificationType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
            assertThat(result.getReadStatus()).isEqualTo(NotificationReadStatus.UNREAD);
        });
    }

    @Test
    void 관리자는_사용자조건_없이_보정된_기본페이징으로_알림을_조회한다() {
        // given
        Pageable requestedPageable = org.mockito.Mockito.mock(Pageable.class);
        given(requestedPageable.getPageNumber()).willReturn(-1);
        given(requestedPageable.getPageSize()).willReturn(20);
        given(requestedPageable.getSort()).willReturn(Sort.unsorted());
        SearchNotificationsCommand command = new SearchNotificationsCommand(
                ADMIN_ID, "ADMIN", " ", " ", requestedPageable);
        Page<Notification> emptyPage = Page.empty(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        given(notificationRepositoryPort.searchNotifications(isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(emptyPage);

        // when
        Page<NotificationSearchResult> resultPage = notificationService.searchNotifications(command);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepositoryPort).searchNotifications(isNull(), isNull(), isNull(), pageableCaptor.capture());
        Pageable normalizedPageable = pageableCaptor.getValue();

        assertThat(normalizedPageable.getPageNumber()).isZero();
        assertThat(normalizedPageable.getPageSize()).isEqualTo(10);
        assertThat(normalizedPageable.getSort().getOrderFor("createdAt")).isNotNull().satisfies(order ->
                assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC));
        assertThat(resultPage).isEmpty();
    }

    @Test
    void 지원하지_않는_알림유형으로는_목록을_조회할_수_없다() {
        // given
        SearchNotificationsCommand command = new SearchNotificationsCommand(
                USER_ID, "USER", "UNKNOWN", null, PageRequest.of(0, 10));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> notificationService.searchNotifications(command), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
        verify(notificationRepositoryPort, never()).searchNotifications(any(), any(), any(), any());
    }

    @Test
    void 지원하지_않는_읽음상태로는_목록을_조회할_수_없다() {
        // given
        SearchNotificationsCommand command = new SearchNotificationsCommand(
                USER_ID, "USER", null, "UNKNOWN", PageRequest.of(0, 10));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> notificationService.searchNotifications(command), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.INVALID_NOTIFICATION_QUERY);
        verify(notificationRepositoryPort, never()).searchNotifications(any(), any(), any(), any());
    }

    @Test
    void 허용하지_않는_필드로는_알림목록을_정렬할_수_없다() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "readAt"));
        SearchNotificationsCommand command = new SearchNotificationsCommand(
                USER_ID, "USER", null, null, pageable);

        // when
        BusinessException exception = catchThrowableOfType(
                () -> notificationService.searchNotifications(command), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.INVALID_NOTIFICATION_QUERY);
        verify(notificationRepositoryPort, never()).searchNotifications(any(), any(), any(), any());
    }

    @Test
    void 사용자는_본인의_알림을_상세조회하면_읽음처리한다() {
        // given
        Notification notification = createNotification(USER_ID);
        ReadNotificationCommand command = new ReadNotificationCommand(USER_ID, "USER", NOTIFICATION_ID);
        Instant requestedAt = Instant.now();

        given(notificationRepositoryPort.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .willReturn(Optional.of(notification));

        // when
        NotificationDetailResult result = notificationService.readNotification(command);

        // then
        verify(notificationRepositoryPort).findByIdAndUserId(NOTIFICATION_ID, USER_ID);
        verify(notificationRepositoryPort, never()).findById(any(UUID.class));
        verify(notificationRepositoryPort, never()).save(any(Notification.class));

        assertThat(notification.getReadStatus()).isEqualTo(NotificationReadStatus.READ);
        assertThat(notification.getLastViewedAt()).isAfterOrEqualTo(requestedAt);
        assertThat(notification.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(result.getNotificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(result.getReservationNumber()).isEqualTo(RESERVATION_NUMBER);
        assertThat(result.getReadStatus()).isEqualTo(NotificationReadStatus.READ);
        assertThat(result.getLastViewedAt()).isEqualTo(notification.getLastViewedAt());
    }

    @Test
    void 사용자는_다른_사용자의_알림을_조회할_수_없다() {
        // given
        ReadNotificationCommand command = new ReadNotificationCommand(USER_ID, "USER", NOTIFICATION_ID);
        given(notificationRepositoryPort.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .willReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                () -> notificationService.readNotification(command), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        verify(notificationRepositoryPort, never()).findById(any(UUID.class));
    }

    @Test
    void 관리자는_본인의_알림을_조회하면_읽음처리한다() {
        // given
        Notification notification = createNotification(ADMIN_ID);
        ReadNotificationCommand command = new ReadNotificationCommand(ADMIN_ID, "ADMIN", NOTIFICATION_ID);

        given(notificationRepositoryPort.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

        // when
        NotificationDetailResult result = notificationService.readNotification(command);

        // then
        verify(notificationRepositoryPort).findById(NOTIFICATION_ID);
        verify(notificationRepositoryPort, never()).findByIdAndUserId(any(UUID.class), any(Long.class));
        assertThat(result.getReadStatus()).isEqualTo(NotificationReadStatus.READ);
        assertThat(result.getLastViewedAt()).isNotNull();
        assertThat(notification.getUpdatedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void 관리자는_다른_사용자의_알림을_조회해도_읽음처리하지_않는다() {
        // given
        Notification notification = createNotification(OTHER_USER_ID);
        ReadNotificationCommand command = new ReadNotificationCommand(ADMIN_ID, "ADMIN", NOTIFICATION_ID);

        given(notificationRepositoryPort.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

        // when
        NotificationDetailResult result = notificationService.readNotification(command);

        // then
        assertThat(result.getReadStatus()).isEqualTo(NotificationReadStatus.UNREAD);
        assertThat(result.getLastViewedAt()).isNull();
        assertThat(notification.getUpdatedBy()).isZero();
        verify(notificationRepositoryPort, never()).save(any(Notification.class));
    }

    @Test
    void 이미_읽은_본인_알림을_다시_조회하면_마지막조회시각을_갱신한다() {
        // given
        Instant previousViewedAt = Instant.parse("2026-09-07T01:00:00Z");
        Notification notification = createNotification(USER_ID);
        notification.markAsRead(USER_ID, previousViewedAt);
        ReadNotificationCommand command = new ReadNotificationCommand(USER_ID, "USER", NOTIFICATION_ID);

        given(notificationRepositoryPort.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .willReturn(Optional.of(notification));

        // when
        NotificationDetailResult result = notificationService.readNotification(command);

        // then
        assertThat(result.getReadStatus()).isEqualTo(NotificationReadStatus.READ);
        assertThat(result.getLastViewedAt()).isAfter(previousViewedAt);
    }

    private Notification createNotification() {
        return createNotification(USER_ID);
    }

    private Notification createNotification(Long userId) {
        Notification notification = Notification.create(
                UUID.randomUUID(), userId, UUID.randomUUID(), RESERVATION_NUMBER,
                NotificationType.RESERVATION_CONFIRMED,
                "[예매완료]", "고객님, 예매가 완료되었습니다.", 0L
        );
        ReflectionTestUtils.setField(notification, "notificationId", NOTIFICATION_ID);
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse("2026-09-08T01:00:00Z"));
        return notification;
    }
}
