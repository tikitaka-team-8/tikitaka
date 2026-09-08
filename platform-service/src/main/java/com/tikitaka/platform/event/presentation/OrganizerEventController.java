package com.tikitaka.platform.event.presentation;

import com.tikitaka.platform.event.application.EventService;
import com.tikitaka.platform.event.presentation.dto.organizer.EventCreateRequest;
import com.tikitaka.platform.event.presentation.dto.organizer.EventCreateResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizers/me/events")
public class OrganizerEventController {

  private final EventService eventService;

  @PostMapping
  // TODO 인가처리
  public ResponseEntity<ApiResponse<EventCreateResponse>> createEvent(
      @RequestHeader("X-User-Id") Long userId,
      @Valid @RequestBody EventCreateRequest request
  ) {

    EventCreateResponse response = eventService.createEvent(userId, request);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            HttpStatus.CREATED,
            "공연 등록 완료되었습니다.",
            response
        ));
  }
}
