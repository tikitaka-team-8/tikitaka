package com.tikitaka.ticketing.global.exception;

import org.springframework.http.HttpStatus;

public record DownstreamErrorCode(
        String code,
        HttpStatus status,
        String message
) implements ErrorCode {
    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
