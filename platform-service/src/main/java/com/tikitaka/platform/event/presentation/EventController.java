package com.tikitaka.platform.event.presentation;

import com.tikitaka.platform.event.application.EventService;
import com.tikitaka.platform.event.presentation.dto.PublicEventListRequest;
import com.tikitaka.platform.event.presentation.dto.PublicEventSummaryResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import com.tikitaka.platform.global.response.PageMeta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
