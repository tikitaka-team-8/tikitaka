package com.tikitaka.gateway.auth.infrastructure;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class GatewayAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationEntryPoint.class);
    private static final String INVALID_ACCESS_TOKEN_CODE = "A-002";
    private static final String INVALID_ACCESS_TOKEN_MESSAGE = "유효하지 않은 인증 정보입니다.";
    private static final String EXPIRED_ACCESS_TOKEN_CODE = "A-003";
    private static final String EXPIRED_ACCESS_TOKEN_MESSAGE = "인증 정보가 만료되었습니다.";

    private final ObjectMapper objectMapper;

    public GatewayAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        boolean expired = isExpired(exception);
        GatewayApiErrorResponse errorResponse = expired
                ? new GatewayApiErrorResponse(
                        Instant.now(),
                        EXPIRED_ACCESS_TOKEN_CODE,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        EXPIRED_ACCESS_TOKEN_MESSAGE
                )
                : new GatewayApiErrorResponse(
                        Instant.now(),
                        INVALID_ACCESS_TOKEN_CODE,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        INVALID_ACCESS_TOKEN_MESSAGE
                );

        log.warn(
                "Gateway 인증 실패: method={}, path={}, code={}",
                request.getMethod(),
                request.getRequestURI(),
                errorResponse.code()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private boolean isExpired(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof JwtValidationException validationException) {
                return validationException.getErrors().stream()
                        .anyMatch(error -> error.getDescription().toLowerCase().contains("expired"));
            }
            current = current.getCause();
        }

        return false;
    }

    private record GatewayApiErrorResponse(
            Instant timestamp,
            String code,
            int status,
            String message
    ) {
    }
}
