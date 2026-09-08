package com.tikitaka.platform.event.presentation;

import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.event.application.SessionSectionPriceService;
import com.tikitaka.platform.event.presentation.dto.SessionSectionPricesResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.SessionSectionPricesCreateRequest;
import com.tikitaka.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizers/me/events/{eventId}/sessions/{sessionId}/section-prices")
public class SessionSectionPriceController {

  private final SessionSectionPriceService sessionSectionPriceService;

  @PutMapping
  public ResponseEntity<ApiResponse<SessionSectionPricesResponse>> createSectionPrice(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody SessionSectionPricesCreateRequest request
      ) {

    SessionSectionPricesResponse response = sessionSectionPriceService.replaceSectionPrices(
        user.userId(),
        eventId,
        sessionId,
        request
    );

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "회차별 좌석 등급 가격이 설정되었습니다.",
            response
        )
    );
  }

  @GetMapping
  public ResponseEntity<ApiResponse<SessionSectionPricesResponse>> getSectionPrices(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID sessionId
  ) {

    SessionSectionPricesResponse response =
        sessionSectionPriceService.getSectionPrices(
        user.userId(),
        eventId,
        sessionId
    );

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "회차별 좌석 등급 가격이 조회되었습니다.",
            response
        )
    );
  }

}
