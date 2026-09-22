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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Auth", description = "회원 인증 API")
public class AuthController {

    private static final String SIGNUP_SUCCESS_MESSAGE = "회원가입이 완료되었습니다.";
    private static final String LOGIN_SUCCESS_MESSAGE = "로그인되었습니다.";
    private static final String REISSUE_SUCCESS_MESSAGE = "인증 토큰을 재발급했습니다.";

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"))
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
    @Operation(summary = "로그인")
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
    @Operation(summary = "인증 토큰 재발급")
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
    @Operation(summary = "로그아웃", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "로그아웃 성공"))
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody AuthLogoutRequest request
    ) {
        authService.logout(authenticatedUser.userId(), request);

        return ResponseEntity.noContent().build();
    }
}
