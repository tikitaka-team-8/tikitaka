package com.tikitaka.platform.auth.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.tikitaka.platform.user.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        createAuthenticatedUser(request).ifPresent(this::setAuthentication);
        filterChain.doFilter(request, response);
    }

    private Optional<AuthenticatedUser> createAuthenticatedUser(
            HttpServletRequest request
    ) {
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        String userRoleHeader = request.getHeader(USER_ROLE_HEADER);

        if (userIdHeader == null || userRoleHeader == null) {
            return Optional.empty();
        }

        try {
            Long userId = Long.valueOf(userIdHeader);
            UserRole role = UserRole.valueOf(userRoleHeader);

            if (userId <= 0) {
                return Optional.empty();
            }

            return Optional.of(new AuthenticatedUser(userId, role));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void setAuthentication(AuthenticatedUser authenticatedUser) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                ROLE_PREFIX + authenticatedUser.role().name()
        );
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        authenticatedUser,
                        null,
                        List.of(authority)
                );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
