package com.tikitaka.platform.auth.presentation.dto;

public record AuthReissueResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn
) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static AuthReissueResponse of(
            String accessToken,
            String refreshToken,
            long expiresIn
    ) {
        return new AuthReissueResponse(
                accessToken,
                refreshToken,
                BEARER_TOKEN_TYPE,
                expiresIn
        );
    }
}
