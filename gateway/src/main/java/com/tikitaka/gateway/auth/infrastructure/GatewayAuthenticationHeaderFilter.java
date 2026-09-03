package com.tikitaka.gateway.auth.infrastructure;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayAuthenticationHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationHeaderFilter.class);
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String ROLE_CLAIM = "role";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();

        String userId = null;
        String role = null;

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && authentication.isAuthenticated()) {
            Jwt jwt = jwtAuthentication.getToken();
            userId = jwt.getSubject();
            role = jwt.getClaimAsString(ROLE_CLAIM);
            log.info(
                    "Gateway 인증 성공: method={}, path={}, userId={}, role={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    userId,
                    role
            );
        }

        filterChain.doFilter(new AuthenticationHeaderRequest(request, userId, role), response);
    }

    private static final class AuthenticationHeaderRequest extends HttpServletRequestWrapper {

        private final String userId;
        private final String role;

        private AuthenticationHeaderRequest(HttpServletRequest request, String userId, String role) {
            super(request);
            this.userId = userId;
            this.role = role;
        }

        @Override
        public String getHeader(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId;
            }
            if (USER_ROLE_HEADER.equalsIgnoreCase(name)) {
                return role;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId == null
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(Set.of(userId));
            }
            if (USER_ROLE_HEADER.equalsIgnoreCase(name)) {
                return role == null
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(Set.of(role));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new LinkedHashSet<>();
            Enumeration<String> originalHeaderNames = super.getHeaderNames();

            while (originalHeaderNames != null && originalHeaderNames.hasMoreElements()) {
                String headerName = originalHeaderNames.nextElement();
                if (!USER_ID_HEADER.equalsIgnoreCase(headerName)
                        && !USER_ROLE_HEADER.equalsIgnoreCase(headerName)) {
                    headerNames.add(headerName);
                }
            }

            if (userId != null) {
                headerNames.add(USER_ID_HEADER);
            }
            if (role != null) {
                headerNames.add(USER_ROLE_HEADER);
            }

            return Collections.enumeration(headerNames);
        }
    }
}
