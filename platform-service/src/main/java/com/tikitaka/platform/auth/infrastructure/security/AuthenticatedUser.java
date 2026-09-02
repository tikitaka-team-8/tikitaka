package com.tikitaka.platform.auth.infrastructure.security;

import com.tikitaka.platform.user.domain.UserRole;

public record AuthenticatedUser(
        Long userId,
        UserRole role
) {
}
