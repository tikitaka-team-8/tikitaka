package com.tikitaka.paymentnotification.global.exception;
import org.springframework.http.HttpStatus;
public enum CommonErrorCode implements ErrorCode {
    UNSUPPORTED_REQUEST("C-001", HttpStatus.BAD_REQUEST, "지원하지 않는 요청입니다."),
    INVALID_INPUT("C-002", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    DOWNSTREAM_SERVICE_FAILURE("C-003", HttpStatus.BAD_GATEWAY, "연동 서비스 처리 중 오류가 발생했습니다."),
    DOWNSTREAM_SERVICE_TIMEOUT("C-004", HttpStatus.GATEWAY_TIMEOUT, "연동 서비스의 응답이 지연되고 있습니다."),
    INTERNAL_SERVER_ERROR("C-005", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE("C-006", HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 이용할 수 없습니다.");
    private final String code;
    private final HttpStatus status;
    private final String message;
    CommonErrorCode(String code, HttpStatus status, String message) {
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
