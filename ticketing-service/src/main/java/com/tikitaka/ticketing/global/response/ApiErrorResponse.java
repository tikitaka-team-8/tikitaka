package com.tikitaka.ticketing.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tikitaka.ticketing.global.exception.ErrorCode;

import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        String traceId,
        String code,
        int status,
        String message,
        Map<String, String> errors
) {
    public static ApiErrorResponse from(ErrorCode errorCode) {
        return from(errorCode, null);
    }

    public static ApiErrorResponse from(ErrorCode errorCode, Map<String, String> errors) {
        return new ApiErrorResponse(
                Instant.now(),
                MDC.get("traceId"),
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorCode.getMessage(),
                errors
        );
    }
}
