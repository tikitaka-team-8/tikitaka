package com.tikitaka.platform.organizer.exception;

import com.tikitaka.platform.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrganizerErrorCode implements ErrorCode {
    ORGANIZER_NOT_FOUND("O-001", HttpStatus.NOT_FOUND, "주최자 정보를 찾을 수 없습니다."),
    ORGANIZER_ALREADY_EXISTS("O-002", HttpStatus.CONFLICT, "이미 등록된 주최자 정보가 있습니다."),
    ORGANIZER_OWNERSHIP_REQUIRED("O-003", HttpStatus.FORBIDDEN, "해당 리소스에 접근할 수 없습니다."),
    INACTIVE_ORGANIZER("O-004", HttpStatus.CONFLICT, "활성화된 주최자만 공연을 관리할 수 있습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    OrganizerErrorCode(String code, HttpStatus status, String message) {
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
