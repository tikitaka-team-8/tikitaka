package com.tikitaka.platform.user.presentation;

import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.global.response.ApiResponse;
import com.tikitaka.platform.user.application.UserService;
import com.tikitaka.platform.user.presentation.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/users")
public class UserController {

    private static final String PROFILE_READ_SUCCESS_MESSAGE = "회원 정보를 조회했습니다.";

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
}
