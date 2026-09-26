package com.tikitaka.platform.event.presentation.controller;

import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.event.application.EventService;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventCreateResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.request.EventStatusUpdateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.response.EventStatusUpdateResponse;
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
@RequestMapping("/api/v1/organizers/me/events")
@Tag(name = "Organizer Event", description = "주최자 공연 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class OrganizerEventController {

  private final EventService eventService;

  @PostMapping
  @Operation(summary = "공연 등록")
  @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "공연 등록 성공"))
  public ResponseEntity<ApiResponse<EventCreateResponse>> createEvent(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody EventCreateRequest request
  ) {

    EventCreateResponse response = eventService.createEvent(user.userId(), request);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            HttpStatus.CREATED,
            "공연 등록 완료되었습니다.",
            response
        ));
  }

  @PatchMapping("/{eventId}/status")
  @Operation(summary = "공연 상태 변경")
  public ResponseEntity<ApiResponse<EventStatusUpdateResponse>> changeStatus(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @Valid @RequestBody EventStatusUpdateRequest request
  ) {

    EventStatusUpdateResponse response = eventService.changeStatus(
        user.userId(),
        eventId,
        request
    );

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "공연 상태를 변경했습니다",
            response
        )
    );
  }
}
