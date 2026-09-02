package com.tikitaka.platform.auth.presentation.dto;

import com.tikitaka.platform.user.domain.User;

public record AuthSignupResponse(
        Long userId,
        String email,
        String nickname,
        String role
) {

    public static AuthSignupResponse from(User user) {
        return new AuthSignupResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole().name()
        );
    }
}
