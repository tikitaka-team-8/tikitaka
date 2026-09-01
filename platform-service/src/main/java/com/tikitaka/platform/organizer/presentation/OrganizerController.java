package com.tikitaka.platform.organizer.presentation;

import com.tikitaka.platform.global.response.ApiResponse;
import com.tikitaka.platform.organizer.application.OrganizerService;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerCreateRequest;
import com.tikitaka.platform.organizer.presentation.dto.OrganizerCreateResponse;
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
  // TODO 인증 인가
  public ResponseEntity<ApiResponse<OrganizerCreateResponse>> create(
      @RequestParam Long userId, // TODO 사용자 정보 규격 확정 후 변경
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
}
