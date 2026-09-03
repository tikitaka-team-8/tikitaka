package com.tikitaka.platform.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthReissueRequest(
        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken
) {
}
