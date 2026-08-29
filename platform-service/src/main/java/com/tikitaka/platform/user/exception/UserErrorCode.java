package com.tikitaka.platform.user.exception;

import com.tikitaka.platform.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS("U-001", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS("U-002", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_INPUT("U-003", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    USER_NOT_FOUND("U-004", HttpStatus.NOT_FOUND, "해당 회원을 찾을 수 없습니다."),
    CURRENT_PASSWORD_MISMATCH("U-005", HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다."),
    INSUFFICIENT_PERMISSION("U-006", HttpStatus.FORBIDDEN, "요청을 수행할 권한이 없습니다."),
    BULK_LOOKUP_LIMIT_EXCEEDED("U-007", HttpStatus.BAD_REQUEST, "한 번에 조회할 수 있는 회원 수를 초과했습니다."),
    WITHDRAWAL_NOT_ALLOWED("U-008", HttpStatus.CONFLICT, "진행 중인 공연이 있어 탈퇴할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    UserErrorCode(String code, HttpStatus status, String message) {
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
