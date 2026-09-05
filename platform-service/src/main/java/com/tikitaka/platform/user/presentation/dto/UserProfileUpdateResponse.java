package com.tikitaka.platform.user.presentation.dto;

import java.time.Instant;

import com.tikitaka.platform.user.domain.User;

public record UserProfileUpdateResponse(
        Long userId,
        String name,
        String nickname,
        String phone,
        Instant updatedAt
) {

    public static UserProfileUpdateResponse from(User user) {
        return new UserProfileUpdateResponse(
                user.getId(),
                user.getName(),
                user.getNickname(),
                user.getPhone(),
                user.getUpdatedAt()
        );
    }
}
