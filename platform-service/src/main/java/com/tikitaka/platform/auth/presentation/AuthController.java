package com.tikitaka.platform.auth.presentation;

import com.tikitaka.platform.auth.application.AuthService;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupResponse;
import com.tikitaka.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String SIGNUP_SUCCESS_MESSAGE = "회원가입이 완료되었습니다.";

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
}
