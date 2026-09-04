package com.tikitaka.platform.event.presentation;

import com.tikitaka.platform.event.application.EventSessionService;
import com.tikitaka.platform.event.presentation.dto.PublicEventSessionDetailResponse;
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
@RequestMapping("/api/v1/events")
public class EventSessionController {

  private final EventSessionService eventSessionService;

  @GetMapping("/{eventId}/sessions/{sessionId}")
  public ResponseEntity<ApiResponse<PublicEventSessionDetailResponse>> getPublicEventSession(
      @PathVariable UUID eventId,
      @PathVariable UUID sessionId
  ) {

    PublicEventSessionDetailResponse response =
        eventSessionService.getPublicEventSession(eventId, sessionId);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "공개 공연 회차 상세 정보를 조회했습니다.",
            response
        )
    );
  }
}
