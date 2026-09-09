package com.tikitaka.platform.event.exception;

import com.tikitaka.platform.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EventErrorCode implements ErrorCode {
    EVENT_NOT_FOUND("E-001", HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
    INVALID_EVENT_STATUS("E-002", HttpStatus.CONFLICT, "현재 공연 상태를 변경할 수 없습니다."),
    INVALID_EVENT_SCHEDULE("E-003", HttpStatus.BAD_REQUEST, "공연 및 판매 시간 설정이 올바르지 않습니다."),
    DUPLICATE_SESSION_NUMBER("E-004", HttpStatus.CONFLICT, "이미 사용중인 회차 번호입니다."),
    EVENT_PUBLICATION_REQUIREMENTS_NOT_MET("E-005", HttpStatus.CONFLICT, "회차와 가격 정책을 모두 설정해야 공연을 공개할 수 있습니다."),
    EVENT_SESSION_MODIFICATION_NOT_ALLOWED("E-006", HttpStatus.CONFLICT, "판매가 시작되었거나 좌석 재고가 생성된 회차입니다."),
    EVENT_SESSION_NOT_FOUND("E-007", HttpStatus.NOT_FOUND, "공연 회차를 찾을 수 없습니다."),
    INVALID_EVENT_SESSION_STATUS("E-008", HttpStatus.CONFLICT, "현재 상태에서는 회차 상태를 변경할 수 없습니다."),
    EVENT_NOT_MODIFIABLE("E-009", HttpStatus.CONFLICT, "현재 공연 상태에서는 정보를 수정할 수 없습니다."),
    EVENT_NOT_RESERVABLE("E-011", HttpStatus.CONFLICT, "현재 예매할 수 없는 공연입니다."),
    EVENT_SESSION_NOT_RESERVABLE("E-012", HttpStatus.CONFLICT, "현재 예매할 수 없는 공연 회차입니다."),
    EVENT_SESSION_CREATE_NOT_ALLOWED("E-013", HttpStatus.CONFLICT, "작성 중인 공연에만 회차를 등록할 수 있습니다."),
    INVALID_SECTION_PRICE("E-014", HttpStatus.BAD_REQUEST, "좌석 등급 또는 가격 설정이 올바르지 않습니다."),
    DUPLICATE_SECTION_PRICE("E-015", HttpStatus.BAD_REQUEST, "같은 공연장 구역의 가격이 중복되었습니다."),
    EVENT_SESSION_REQUIRED("E-016", HttpStatus.CONFLICT, "공연 공개를 위해 최소 한 개의 회차가 필요합니다."),
    EVENT_SESSION_NOT_SCHEDULED("E-017", HttpStatus.CONFLICT, "모든 공연 회차가 SCHEDULED 상태여야 합니다."),
    ACTIVE_VENUE_SEAT_REQUIRED("E-018", HttpStatus.CONFLICT, "공연장에 활성 좌석이 존재하지 않습니다."),
    SECTION_PRICE_REQUIRED("E-019", HttpStatus.CONFLICT, "활성 좌석이 있는 모든 구역에 가격 정책을 설정해야 합니다."),
    SECTION_PRICE_VENUE_MISMATCH("E-020", HttpStatus.CONFLICT, "가격 정책의 구역이 해당 공연장에 속하지 않습니다."),
  SELLABLE_SECTION_PRICE_REQUIRED("E-021", HttpStatus.CONFLICT, "각 회차에 판매 가능한 가격 정책이 최소 한 개 필요합니다."),
  ;

  private final String code;
    private final HttpStatus status;
    private final String message;

    EventErrorCode(String code, HttpStatus status, String message) {
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
