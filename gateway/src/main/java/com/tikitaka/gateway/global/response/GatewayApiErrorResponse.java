package com.tikitaka.gateway.global.response;

import java.time.Instant;

import org.slf4j.MDC;

public record GatewayApiErrorResponse(
        Instant timestamp,
        String traceId,
        String code,
        int status,
        String message
) {

    public static GatewayApiErrorResponse of(String code, int status, String message) {
        return new GatewayApiErrorResponse(
                Instant.now(),
                MDC.get("traceId"),
                code,
                status,
                message
        );
    }
}
