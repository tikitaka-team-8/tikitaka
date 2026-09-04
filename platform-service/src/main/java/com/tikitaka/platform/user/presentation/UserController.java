package com.tikitaka.platform.user.presentation;

import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.global.response.ApiResponse;
import com.tikitaka.platform.user.application.UserService;
import com.tikitaka.platform.user.presentation.dto.UserProfileResponse;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateRequest;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private static final String PROFILE_READ_SUCCESS_MESSAGE = "회원 정보를 조회했습니다.";
    private static final String PROFILE_UPDATE_SUCCESS_MESSAGE = "회원 정보를 수정했습니다.";

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        UserProfileResponse response = userService.getMyProfile(authenticatedUser.userId());

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                PROFILE_READ_SUCCESS_MESSAGE,
                response
        ));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileUpdateResponse>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        UserProfileUpdateResponse response = userService.updateMyProfile(authenticatedUser.userId(), request);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                PROFILE_UPDATE_SUCCESS_MESSAGE,
                response
        ));
    }
}
