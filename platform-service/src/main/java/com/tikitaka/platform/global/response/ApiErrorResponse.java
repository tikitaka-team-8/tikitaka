package com.tikitaka.platform.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tikitaka.platform.global.exception.ErrorCode;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
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
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorCode.getMessage(),
                errors
        );
    }
}
