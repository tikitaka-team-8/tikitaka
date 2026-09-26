package com.tikitaka.platform.event.presentation.controller;

import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.event.application.SessionSectionPriceService;
import com.tikitaka.platform.event.presentation.dto.SessionSectionPricesResponse;
import com.tikitaka.platform.event.presentation.dto.organizer.request.SessionSectionPricesCreateRequest;
import com.tikitaka.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizers/me/events/{eventId}/sessions/{sessionId}/section-prices")
@Tag(name = "Session Section Price", description = "공연 회차 구역별 가격 API")
@SecurityRequirement(name = "bearerAuth")
public class SessionSectionPriceController {

  private final SessionSectionPriceService sessionSectionPriceService;

  @PutMapping
  @Operation(summary = "회차 구역별 가격 설정")
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
  @Operation(summary = "회차 구역별 가격 조회")
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
