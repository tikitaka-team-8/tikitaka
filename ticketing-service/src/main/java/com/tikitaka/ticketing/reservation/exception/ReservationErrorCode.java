package com.tikitaka.ticketing.reservation.exception;

import com.tikitaka.ticketing.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReservationErrorCode implements ErrorCode {
    RESERVATION_NOT_FOUND("R-001", HttpStatus.NOT_FOUND, "해당 예매를 찾을 수 없습니다."),
    SEAT_HOLD_NOT_FOUND("R-002", HttpStatus.NOT_FOUND, "선택한 좌석을 예매할 수 없습니다."),
    INVALID_INPUT("R-003", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    SEAT_HOLD_EXPIRED("R-004", HttpStatus.GONE, "좌석 선점 시간이 만료되었습니다. 좌석을 다시 선택해주세요."),
    SEAT_HOLD_OWNERSHIP_REQUIRED("R-005", HttpStatus.FORBIDDEN, "본인이 선점한 좌석만 예매할 수 있습니다."),
    INVALID_SEAT_HOLD_STATUS("R-006", HttpStatus.CONFLICT, "이미 종료되었거나 사용할 수 없는 좌석 선점입니다."),
    RESERVATION_ALREADY_EXISTS("R-007", HttpStatus.CONFLICT, "해당 좌석 선점으로 생성된 예매가 이미 존재합니다."),
    PAYMENT_CREATION_FAILED("R-008", HttpStatus.SERVICE_UNAVAILABLE, "결제 정보를 생성하지 못했습니다. 잠시 후 다시 시도해주세요."),
    RESERVATION_CANCELLATION_NOT_ALLOWED("R-009", HttpStatus.CONFLICT, "현재 상태에서는 예매를 취소할 수 없습니다."),
    RESERVATION_CANCELLATION_PERIOD_EXPIRED("R-010", HttpStatus.CONFLICT, "예매 취소 가능 시간이 지났습니다."),
    RESERVATION_ALREADY_CANCELLED("R-011", HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    PAYMENT_IN_PROGRESS("R-012", HttpStatus.CONFLICT, "결제 처리 중에는 예매를 취소할 수 없습니다."),
    REFUND_REQUEST_FAILED("R-013", HttpStatus.BAD_GATEWAY, "결제 환불 요청에 실패했습니다. 잠시 후 다시 시도해주세요."),
    INVALID_RESERVATION_STATUS_TRANSITION("R-014", HttpStatus.CONFLICT, "현재 예매 상태에서는 요청한 상태로 변경할 수 없습니다."),
    RESERVATION_PAYMENT_NOT_ALLOWED("R-015", HttpStatus.CONFLICT, "현재 예매 상태에서는 결제를 진행할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ReservationErrorCode(String code, HttpStatus status, String message) {
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
