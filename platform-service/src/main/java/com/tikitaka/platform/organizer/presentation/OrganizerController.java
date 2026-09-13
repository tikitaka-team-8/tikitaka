package com.tikitaka.platform.organizer.presentation;

import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.global.response.ApiResponse;
import com.tikitaka.platform.organizer.application.OrganizerService;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerCreateRequest;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerCreateResponse;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerDetailResponse;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizers")
public class OrganizerController {

  private final OrganizerService organizerService;

  @PostMapping
  public ResponseEntity<ApiResponse<OrganizerCreateResponse>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody OrganizerCreateRequest request
  ) {

    OrganizerCreateResponse response =
        organizerService.createOrganizer(request.toCommand(user.userId()));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            HttpStatus.CREATED,
            "주최자 등록 신청이 완료되었습니다.",
            response
        ));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<OrganizerDetailResponse>> getMyOrganizer(
      @AuthenticationPrincipal AuthenticatedUser user
  ) {
    OrganizerDetailResponse response = organizerService.getMyOrganizer(user.userId());

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "주최자 정보를 조회했습니다.",
            response
        )
    );
  }

  @PatchMapping("/me")
  public ResponseEntity<ApiResponse<OrganizerDetailResponse>> updateMyOrganizer(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody OrganizerUpdateRequest request
  ) {
    OrganizerDetailResponse response =
        organizerService.updateOrganizer(request.toCommand(user.userId()));

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "주최자 정보가 수정되었습니다.",
            response
        )
    );
  }
}
