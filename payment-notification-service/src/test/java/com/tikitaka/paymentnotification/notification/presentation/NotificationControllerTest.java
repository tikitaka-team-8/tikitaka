package com.tikitaka.paymentnotification.notification.presentation;

import com.tikitaka.paymentnotification.notification.application.command.ReadNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.service.NotificationService;
import com.tikitaka.paymentnotification.notification.application.command.SearchNotificationsCommand;
import com.tikitaka.paymentnotification.notification.application.result.NotificationDetailResult;
import com.tikitaka.paymentnotification.notification.application.result.NotificationSearchResult;
import com.tikitaka.paymentnotification.notification.domain.entity.Notification;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationReadStatus;
import com.tikitaka.paymentnotification.notification.domain.enums.NotificationType;
import com.tikitaka.paymentnotification.notification.presentation.controller.NotificationController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class NotificationControllerTest {

    private static final Long USER_ID = 1L;
    private static final UUID NOTIFICATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String RESERVATION_NUMBER = "RSV-260908-ABCDEF123456";
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T01:00:00Z");
    private static final Instant LAST_VIEWED_AT = Instant.parse("2026-09-08T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void 사용자의_검색조건과_페이징으로_알림목록을_조회한다() throws Exception {
        // given
        PageRequest pageable = PageRequest.of(1, 30, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<NotificationSearchResult> resultPage = new PageImpl<>(
                List.of(createSearchResult()), pageable, 31);
        given(notificationService.searchNotifications(any(SearchNotificationsCommand.class))).willReturn(resultPage);

        // when
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .param("page", "1")
                        .param("size", "30")
                        .param("sort", "createdAt,asc")
                        .param("notificationType", "RESERVATION_CONFIRMED")
                        .param("readStatus", "UNREAD"))

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data[0].notificationId").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.data[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.data[0].notificationType").value("RESERVATION_CONFIRMED"))
                .andExpect(jsonPath("$.data[0].readStatus").value("UNREAD"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(30))
                .andExpect(jsonPath("$.meta.totalElements").value(31))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(false));

        ArgumentCaptor<SearchNotificationsCommand> commandCaptor =
                ArgumentCaptor.forClass(SearchNotificationsCommand.class);
        verify(notificationService).searchNotifications(commandCaptor.capture());
        SearchNotificationsCommand command = commandCaptor.getValue();

        assertThat(command.getLoginUserId()).isEqualTo(USER_ID);
        assertThat(command.getUserRole()).isEqualTo("USER");
        assertThat(command.getNotificationType()).isEqualTo("RESERVATION_CONFIRMED");
        assertThat(command.getReadStatus()).isEqualTo("UNREAD");
        assertThat(command.getPageable().getPageNumber()).isEqualTo(1);
        assertThat(command.getPageable().getPageSize()).isEqualTo(30);
        assertThat(command.getPageable().getSort().getOrderFor("createdAt")).isNotNull().satisfies(order ->
                assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
    }

    @Test
    void 필수_사용자역할_헤더가_없으면_입력값오류를_반환한다() throws Exception {
        // given
        String requestPath = "/api/v1/notifications";

        // when
        mockMvc.perform(get(requestPath)
                        .header("X-User-Id", USER_ID))

                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C-002"))
                .andExpect(jsonPath("$['errors']['X-User-Role']").value("필수 요청 헤더입니다."));

        verify(notificationService, never()).searchNotifications(any(SearchNotificationsCommand.class));
    }

    @Test
    void 알림목록조회시_허용하지_않는_역할이면_입력값오류를_반환한다() throws Exception {
        // given
        String requestPath = "/api/v1/notifications";

        // when
        mockMvc.perform(get(requestPath)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "MANAGER"))

                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C-002"));

        verify(notificationService, never()).searchNotifications(any(SearchNotificationsCommand.class));
    }

    @Test
    void 사용자는_알림을_상세조회하고_읽음처리한다() throws Exception {
        // given
        given(notificationService.readNotification(any(ReadNotificationCommand.class)))
                .willReturn(createDetailResult());

        // when
        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", NOTIFICATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.notificationId").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.data.reservationNumber").value(RESERVATION_NUMBER))
                .andExpect(jsonPath("$.data.notificationType").value("RESERVATION_CONFIRMED"))
                .andExpect(jsonPath("$.data.readStatus").value("READ"))
                .andExpect(jsonPath("$.data.lastViewedAt").value(LAST_VIEWED_AT.toString()))
                .andExpect(jsonPath("$.data.createdAt").value(CREATED_AT.toString()));

        ArgumentCaptor<ReadNotificationCommand> commandCaptor =
                ArgumentCaptor.forClass(ReadNotificationCommand.class);
        verify(notificationService).readNotification(commandCaptor.capture());
        ReadNotificationCommand command = commandCaptor.getValue();

        assertThat(command.getLoginUserId()).isEqualTo(USER_ID);
        assertThat(command.getUserRole()).isEqualTo("USER");
        assertThat(command.getNotificationId()).isEqualTo(NOTIFICATION_ID);
    }

    @Test
    void 알림상세조회시_필수_사용자아이디_헤더가_없으면_입력값오류를_반환한다() throws Exception {
        // given
        String requestPath = "/api/v1/notifications/" + NOTIFICATION_ID + "/read";

        // when
        mockMvc.perform(patch(requestPath)
                        .header("X-User-Role", "USER"))

                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C-002"))
                .andExpect(jsonPath("$['errors']['X-User-Id']").value("필수 요청 헤더입니다."));

        verify(notificationService, never()).readNotification(any(ReadNotificationCommand.class));
    }

    @Test
    void 알림상세조회시_허용하지_않는_역할이면_입력값오류를_반환한다() throws Exception {
        // given
        String requestPath = "/api/v1/notifications/" + NOTIFICATION_ID + "/read";

        // when
        mockMvc.perform(patch(requestPath)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "MANAGER"))

                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C-002"));

        verify(notificationService, never()).readNotification(any(ReadNotificationCommand.class));
    }

    private NotificationSearchResult createSearchResult() {
        Notification notification = Notification.create(
                UUID.randomUUID(), USER_ID, UUID.randomUUID(), RESERVATION_NUMBER,
                NotificationType.RESERVATION_CONFIRMED,
                "[예매완료]", "고객님, 예매가 완료되었습니다.", 0L
        );
        ReflectionTestUtils.setField(notification, "notificationId", NOTIFICATION_ID);
        ReflectionTestUtils.setField(notification, "createdAt", CREATED_AT);
        return new NotificationSearchResult(notification);
    }

    private NotificationDetailResult createDetailResult() {
        Notification notification = Notification.create(
                UUID.randomUUID(), USER_ID, RESERVATION_ID, RESERVATION_NUMBER,
                NotificationType.RESERVATION_CONFIRMED,
                "[예매완료]", "고객님, 예매가 완료되었습니다.", 0L
        );
        ReflectionTestUtils.setField(notification, "notificationId", NOTIFICATION_ID);
        ReflectionTestUtils.setField(notification, "createdAt", CREATED_AT);
        notification.markAsRead(USER_ID, LAST_VIEWED_AT);
        return new NotificationDetailResult(notification);
    }
}
