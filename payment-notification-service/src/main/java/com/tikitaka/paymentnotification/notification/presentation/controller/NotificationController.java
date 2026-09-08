package com.tikitaka.paymentnotification.notification.presentation.controller;

import com.tikitaka.paymentnotification.global.response.ApiResponse;
import com.tikitaka.paymentnotification.global.response.PageMeta;
import com.tikitaka.paymentnotification.notification.application.command.ReadNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.command.SearchNotificationsCommand;
import com.tikitaka.paymentnotification.notification.application.result.NotificationDetailResult;
import com.tikitaka.paymentnotification.notification.application.result.NotificationSearchResult;
import com.tikitaka.paymentnotification.notification.application.service.NotificationService;
import com.tikitaka.paymentnotification.notification.presentation.dto.request.NotificationSearchReqDto;
import com.tikitaka.paymentnotification.notification.presentation.dto.response.NotificationDetailResDto;
import com.tikitaka.paymentnotification.notification.presentation.dto.response.NotificationSearchResDto;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    // TODO: 헤더 상수 사용으로 중복 코드 삭제
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationSearchResDto>>> searchNotifications(
            @RequestHeader(USER_ID_HEADER) Long loginUserId,
            @RequestHeader(USER_ROLE_HEADER) @Pattern(regexp = "USER|ADMIN") String userRole,
            @ModelAttribute NotificationSearchReqDto requestDto,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        SearchNotificationsCommand command = new SearchNotificationsCommand(
                loginUserId, userRole, requestDto.getNotificationType(), requestDto.getReadStatus(), pageable
        );

        Page<NotificationSearchResult> resultPage = notificationService.searchNotifications(command);

        List<NotificationSearchResDto> response = resultPage.getContent().stream()
                .map(NotificationSearchResDto::new).toList();

        PageMeta meta = new PageMeta(
                resultPage.getNumber(), resultPage.getSize(), resultPage.getTotalElements(),
                resultPage.getTotalPages(), resultPage.hasNext()
        );

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "알림 목록 조회에 성공했습니다.", response, meta)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationDetailResDto>> readNotification(
            @RequestHeader(USER_ID_HEADER) @Positive Long loginUserId,
            @RequestHeader(USER_ROLE_HEADER) @Pattern(regexp = "USER|ADMIN") String userRole,
            @PathVariable UUID notificationId) {

        ReadNotificationCommand command = new ReadNotificationCommand(loginUserId, userRole, notificationId);
        NotificationDetailResult result = notificationService.readNotification(command);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "알림 상세 조회에 성공했습니다.", new NotificationDetailResDto(result))
        );
    }
}
