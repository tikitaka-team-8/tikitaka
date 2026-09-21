package com.tikitaka.gateway.auth.infrastructure;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.gateway.global.response.GatewayApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class GatewayAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccessDeniedHandler.class);
    private static final String ACCESS_DENIED_CODE = "U-006";
    private static final String ACCESS_DENIED_MESSAGE = "요청을 수행할 권한이 없습니다.";

    private final ObjectMapper objectMapper;

    public GatewayAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        GatewayApiErrorResponse errorResponse = GatewayApiErrorResponse.of(
                ACCESS_DENIED_CODE,
                HttpServletResponse.SC_FORBIDDEN,
                ACCESS_DENIED_MESSAGE
        );

        log.warn(
                "Gateway 접근 거부: method={}, path={}, code={}",
                request.getMethod(),
                request.getRequestURI(),
                errorResponse.code()
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
