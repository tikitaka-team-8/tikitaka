package com.tikitaka.platform.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalServiceKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_PREFIX = "/api/v1/internal/";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";

    private final byte[] expectedServiceKey;

    public InternalServiceKeyAuthenticationFilter(String expectedServiceKey) {
        this.expectedServiceKey = expectedServiceKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        List<String> serviceKeys = Collections.list(request.getHeaders(SERVICE_KEY_HEADER));

        if (serviceKeys.size() != 1 || !matches(serviceKeys.getFirst())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String actualServiceKey) {
        return actualServiceKey != null && MessageDigest.isEqual(
                expectedServiceKey,
                actualServiceKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
