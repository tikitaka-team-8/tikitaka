package com.tikitaka.platform.auth.presentation.dto;

public record AuthLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn
) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static AuthLoginResponse of(
            String accessToken,
            String refreshToken,
            long expiresIn
    ) {
        return new AuthLoginResponse(
                accessToken,
                refreshToken,
                BEARER_TOKEN_TYPE,
                expiresIn
        );
    }
}
