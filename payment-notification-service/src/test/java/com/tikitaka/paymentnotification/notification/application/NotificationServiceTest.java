package com.tikitaka.paymentnotification.notification.application;

import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.notification.application.command.SearchNotificationsCommand;
import com.tikitaka.paymentnotification.notification.application.result.NotificationSearchResult;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
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
    private static final UUID NOTIFICATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

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

    private Notification createNotification() {
        Notification notification = Notification.create(
                UUID.randomUUID(), USER_ID, UUID.randomUUID(), NotificationType.RESERVATION_CONFIRMED,
                "[예매완료]", "고객님, 예매가 완료되었습니다.", 0L
        );
        ReflectionTestUtils.setField(notification, "notificationId", NOTIFICATION_ID);
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse("2026-09-08T01:00:00Z"));
        return notification;
    }
}
