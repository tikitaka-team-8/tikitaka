package com.tikitaka.platform.user.presentation.dto;

import com.tikitaka.platform.user.domain.User;

import java.time.Instant;

public record UserProfileResponse(
        Long userId,
        String email,
        String name,
        String nickname,
        String phone,
        String role,
        String status,
        Instant createdAt
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getPhone(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
