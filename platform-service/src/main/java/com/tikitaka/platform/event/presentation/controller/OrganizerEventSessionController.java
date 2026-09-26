package com.tikitaka.platform.event.presentation.controller;

import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.event.application.EventSessionService;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventSessionCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventSessionCreateResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizers/me/events/{eventId}/sessions")
@Tag(name = "Organizer Event Session", description = "주최자 공연 회차 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class OrganizerEventSessionController {

  private final EventSessionService eventSessionService;

  @PostMapping
  @Operation(summary = "공연 회차 등록")
  @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "공연 회차 등록 성공"))
  public ResponseEntity<ApiResponse<EventSessionCreateResponse>> createEventSession(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @Valid @RequestBody EventSessionCreateRequest request
  ) {

    EventSessionCreateResponse response =
        eventSessionService.createEventSession(user.userId(), eventId, request);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            HttpStatus.CREATED,
            "공연 회차가 등록되었습니다.",
            response
        ));
  }
}
