package com.tikitaka.ticketing.seat.exception;

import com.tikitaka.ticketing.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SeatErrorCode implements ErrorCode {
    SESSION_OR_SEAT_NOT_FOUND("S-001", HttpStatus.NOT_FOUND, "요청한 좌석 정보를 찾을 수 없습니다."),
    SEAT_HOLD_NOT_FOUND("S-002", HttpStatus.NOT_FOUND, "요청한 선점 정보를 찾을 수 없습니다."),
    SEAT_UNAVAILABLE("S-003", HttpStatus.CONFLICT, "선택한 좌석은 이미 선점되었거나 판매가 완료되었습니다."),
    SEAT_NOT_FOR_SALE("S-004", HttpStatus.GONE, "판매 대상이 아닌 좌석입니다."),
    INVALID_QUEUE_TOKEN("S-005", HttpStatus.UNAUTHORIZED, "입장 토큰이 유효하지 않습니다. 대기열을 다시 진행해 주세요."),
    LOGIN_REQUIRED("S-006", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    SEAT_HOLD_OWNERSHIP_REQUIRED("S-007", HttpStatus.FORBIDDEN, "본인이 선점한 좌석만 처리할 수 있습니다."),
    SEAT_HOLD_ALREADY_CLOSED("S-008", HttpStatus.CONFLICT, "이미 처리가 종료된 선점 건입니다."),
    INVALID_INPUT("S-009", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    SEAT_STATUS_CONFLICT("S-010", HttpStatus.CONFLICT, "요청을 처리할 수 없는 상태입니다. 잠시 후 다시 시도해 주세요."),
    SEAT_READ_PERMISSION_REQUIRED("S-011", HttpStatus.FORBIDDEN, "조회 권한이 없습니다."),
    INTERNAL_SERVICE_AUTHENTICATION_FAILED("S-012", HttpStatus.UNAUTHORIZED, "내부 서비스 인증에 실패했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    SeatErrorCode(String code, HttpStatus status, String message) {
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
