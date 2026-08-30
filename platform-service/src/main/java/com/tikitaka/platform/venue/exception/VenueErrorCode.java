package com.tikitaka.platform.venue.exception;

import com.tikitaka.platform.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum VenueErrorCode implements ErrorCode {
    VENUE_NOT_FOUND("V-001", HttpStatus.NOT_FOUND, "공연장을 찾을 수 없습니다."),
    VENUE_SECTION_NOT_FOUND("V-002", HttpStatus.NOT_FOUND, "공연장 구역을 찾을 수 없습니다."),
    VENUE_SEAT_NOT_FOUND("V-003", HttpStatus.NOT_FOUND, "공연장 좌석을 찾을 수 없습니다."),
    DUPLICATE_VENUE_COMPONENT("V-004", HttpStatus.CONFLICT, "같은 이름 또는 좌석 번호가 이미 존재합니다."),
    VENUE_COMPONENT_IN_USE("V-005", HttpStatus.CONFLICT, "예정된 공연에서 사용중입니다."),
    VENUE_SECTION_MISMATCH("V-006", HttpStatus.BAD_REQUEST, "공연장에 속하지 않은 구역입니다."),
    INACTIVE_VENUE("V-007", HttpStatus.CONFLICT, "비활성화된 공연장은 선택할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    VenueErrorCode(String code, HttpStatus status, String message) {
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
