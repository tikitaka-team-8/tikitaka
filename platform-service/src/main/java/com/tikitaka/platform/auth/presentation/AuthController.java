package com.tikitaka.platform.auth.presentation;

import com.tikitaka.platform.auth.application.AuthService;
import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.auth.presentation.dto.AuthLoginRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthLoginResponse;
import com.tikitaka.platform.auth.presentation.dto.AuthLogoutRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthReissueRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthReissueResponse;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String SIGNUP_SUCCESS_MESSAGE = "회원가입이 완료되었습니다.";
    private static final String LOGIN_SUCCESS_MESSAGE = "로그인되었습니다.";
    private static final String REISSUE_SUCCESS_MESSAGE = "인증 토큰을 재발급했습니다.";

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthSignupResponse>> signUp(
            @Valid @RequestBody AuthSignupRequest request
    ) {
        AuthSignupResponse response = authService.signUp(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        SIGNUP_SUCCESS_MESSAGE,
                        response
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthLoginResponse>> login(
            @Valid @RequestBody AuthLoginRequest request
    ) {
        AuthLoginResponse response = authService.login(request);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                LOGIN_SUCCESS_MESSAGE,
                response
        ));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<AuthReissueResponse>> reissue(
            @Valid @RequestBody AuthReissueRequest request
    ) {
        AuthReissueResponse response = authService.reissue(request);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                REISSUE_SUCCESS_MESSAGE,
                response
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody AuthLogoutRequest request
    ) {
        authService.logout(authenticatedUser.userId(), request);

        return ResponseEntity.noContent().build();
    }
}
