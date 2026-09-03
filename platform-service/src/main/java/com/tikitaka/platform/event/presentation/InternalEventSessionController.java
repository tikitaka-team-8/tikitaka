package com.tikitaka.platform.event.presentation;

import com.tikitaka.platform.event.application.EventSessionService;
import com.tikitaka.platform.event.presentation.dto.QueueSalesStatusResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/event-sessions")
public class InternalEventSessionController {

  private final EventSessionService eventSessionService;

  @GetMapping("/{sessionId}/sales-status")
  public ResponseEntity<ApiResponse<QueueSalesStatusResponse>> getQueueSalseStatus(
      @PathVariable UUID sessionId
  ) {

    QueueSalesStatusResponse response =
        eventSessionService.getQueueSalseStatus(sessionId);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "대기열 진입 회차 정보를 조회했습니다.",
            response
        )
    );
  }
}