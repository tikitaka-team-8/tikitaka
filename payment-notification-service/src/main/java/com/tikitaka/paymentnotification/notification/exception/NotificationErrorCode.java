package com.tikitaka.paymentnotification.notification.exception;

import com.tikitaka.paymentnotification.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {
    INVALID_NOTIFICATION_QUERY("N-001", HttpStatus.BAD_REQUEST, "알림 조회 조건이 올바르지 않습니다."),
    UNSUPPORTED_NOTIFICATION_TYPE("N-002", HttpStatus.BAD_REQUEST, "알림 유형이 올바르지 않습니다."),
    NOTIFICATION_NOT_FOUND("N-003", HttpStatus.NOT_FOUND, "해당 알림을 찾을 수 없습니다."),
    NOTIFICATION_PROCESSING_FAILED("N-004", HttpStatus.INTERNAL_SERVER_ERROR, "알림을 처리하는 중 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    NotificationErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

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
