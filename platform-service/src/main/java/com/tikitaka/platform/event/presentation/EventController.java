package com.tikitaka.platform.event.presentation;

import com.tikitaka.platform.event.application.EventService;
import com.tikitaka.platform.event.presentation.dto.PublicEventDetailResponse;
import com.tikitaka.platform.event.presentation.dto.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.PublicEventSummaryResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import com.tikitaka.platform.global.response.PageMeta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class EventController {

  private final EventService eventService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<PublicEventSummaryResponse>>> getPublicEvents(
      @Valid @ModelAttribute PublicEventListRequest request
  ) {
    Page<PublicEventSummaryResponse> responses =
        eventService.getPublicEvents(request);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "공연 목록을 조회했습니다",
            responses.getContent(),
            PageMeta.from(responses)
        )
    );
  }

  @GetMapping("/{eventId}")
  public ResponseEntity<ApiResponse<PublicEventDetailResponse>> getPublicEvent(
      @PathVariable UUID eventId
  ) {
    PublicEventDetailResponse response = eventService.getPublicEvent(eventId);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "공연 상세 정보를 조회했습니다",
            response
        )
    );
  }
}
