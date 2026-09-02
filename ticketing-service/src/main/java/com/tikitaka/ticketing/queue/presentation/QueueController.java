package com.tikitaka.ticketing.queue.presentation;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/queues/sessions/{sessionId}/entries")
    public ApiResponse<QueueEntryResponse> enterQueue(
            @PathVariable UUID sessionId,
            @RequestHeader("X-User-Id") @Positive long userId
    ) {
        QueueEntry entry = queueService.enterQueue(sessionId, userId);
        return ApiResponse.success(HttpStatus.OK, "대기열에 진입했습니다.", QueueEntryResponse.from(entry));
    }

    @GetMapping("/queues/sessions/{sessionId}/entries/me")
    public ApiResponse<QueueEntryResponse> getMyQueueEntry(
            @PathVariable UUID sessionId,
            @RequestHeader("X-User-Id") @Positive long userId
    ) {
        QueueEntry entry = queueService.getEntry(sessionId, userId);
        return ApiResponse.success(HttpStatus.OK, "대기열 상태를 조회했습니다.", QueueEntryResponse.from(entry));
    }
}
