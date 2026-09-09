package com.tikitaka.platform.event.presentation.controller;

import com.tikitaka.platform.event.application.EventSessionService;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventSessionCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventSessionCreateResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizers/me/events/{eventId}/sessions")
public class OrganizerEventSessionController {

  private final EventSessionService eventSessionService;

  @PostMapping
  public ResponseEntity<ApiResponse<EventSessionCreateResponse>> createEventSession(
      @RequestHeader("X-User-Id") Long userId,
      @PathVariable UUID eventId,
      @Valid @RequestBody EventSessionCreateRequest request
  ) {

    EventSessionCreateResponse response =
        eventSessionService.createEventSession(userId, eventId, request);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            HttpStatus.CREATED,
            "공연 회차가 등록되었습니다.",
            response
        ));
  }
}
