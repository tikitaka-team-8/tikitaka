package com.tikitaka.ticketing.queue.presentation;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.queue.application.QueueStatusResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Queue", description = "공연 회차 대기열 API")
@SecurityRequirement(name = "bearerAuth")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/event-sessions/{sessionId}/queue")
    @Operation(summary = "대기열 진입")
    public ApiResponse<QueueEntryResponse> enterQueue(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") @Positive long userId
    ) {
        queueService.enterQueue(sessionId, userId);
        QueueStatusResult result = queueService.getQueueStatus(sessionId, userId);
        return ApiResponse.success(HttpStatus.OK, "대기열에 진입했습니다.", QueueEntryResponse.from(result));
    }

    @GetMapping("/event-sessions/{sessionId}/queue/me")
    @Operation(summary = "내 대기열 상태 조회")
    public ApiResponse<QueueEntryResponse> getMyQueueEntry(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") @Positive long userId
    ) {
        QueueStatusResult result = queueService.getQueueStatus(sessionId, userId);
        return ApiResponse.success(HttpStatus.OK, "대기열 상태를 조회했습니다.", QueueEntryResponse.from(result));
    }

    @DeleteMapping("/event-sessions/{sessionId}/queue/me")
    @Operation(summary = "대기열 이탈")
    public ApiResponse<QueueLeaveResponse> leaveQueue(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") @Positive long userId
    ) {
        queueService.leaveQueue(sessionId, userId);
        return ApiResponse.success(HttpStatus.OK, "대기열에서 이탈했습니다.", QueueLeaveResponse.left(sessionId));
    }

    @PostMapping("/event-sessions/{sessionId}/queue/me/heartbeat")
    @Operation(summary = "대기열 heartbeat 갱신")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "heartbeat 갱신 성공"))
    public ResponseEntity<Void> refreshWaitingHeartbeat(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") @Positive long userId
    ) {
        queueService.refreshWaitingHeartbeat(sessionId, userId);
        return ResponseEntity.noContent().build();
    }
}
