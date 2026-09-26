package com.tikitaka.platform.event.presentation.controller;

import com.tikitaka.platform.event.application.EventService;
import com.tikitaka.platform.event.presentation.dto.query.PublicEventDetailResponse;
import com.tikitaka.platform.event.presentation.dto.query.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.query.PublicEventSummaryResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import com.tikitaka.platform.global.response.PageMeta;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Event", description = "공개 공연 조회 API")
public class EventController {

  private final EventService eventService;

  @GetMapping
  @Operation(summary = "공개 공연 목록 조회")
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
  @Operation(summary = "공개 공연 상세 조회")
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
