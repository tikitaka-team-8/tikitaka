package com.tikitaka.platform.auth.exception;

import com.tikitaka.platform.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("A-001", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_ACCESS_TOKEN("A-002", HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),
    EXPIRED_ACCESS_TOKEN("A-003", HttpStatus.UNAUTHORIZED, "인증 정보가 만료되었습니다."),
    INVALID_REFRESH_TOKEN("A-004", HttpStatus.UNAUTHORIZED, "유효하지 않은 갱신 토큰입니다."),
    REVOKED_REFRESH_TOKEN("A-005", HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    INACTIVE_ACCOUNT("A-006", HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다."),
    AUTH_REQUEST_LIMIT_EXCEEDED("A-007", HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    AuthErrorCode(String code, HttpStatus status, String message) {
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
