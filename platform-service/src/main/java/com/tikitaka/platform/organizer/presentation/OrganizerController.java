package com.tikitaka.platform.organizer.presentation;

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
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizers")
public class OrganizerController {

  private final OrganizerService organizerService;

  @PostMapping
  public ResponseEntity<ApiResponse<OrganizerCreateResponse>> create(
      @RequestHeader("X-User-Id") Long userId,
      @Valid @RequestBody OrganizerCreateRequest request
  ) {

    OrganizerCreateResponse response =
        organizerService.createOrganizer(request.toCommand(userId));

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
      @RequestHeader("X-User-Id") Long userId
  ) {
    OrganizerDetailResponse response = organizerService.getMyOrganizer(userId);

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
      @RequestHeader("X-User-Id") Long userId,
      @Valid @RequestBody OrganizerUpdateRequest request
  ) {
    OrganizerDetailResponse response =
        organizerService.updateOrganizer(request.toCommand(userId));

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "주최자 정보가 수정되었습니다.",
            response
        )
    );
  }
}
