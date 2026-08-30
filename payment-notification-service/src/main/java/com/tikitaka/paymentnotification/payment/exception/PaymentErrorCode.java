package com.tikitaka.paymentnotification.payment.exception;

import com.tikitaka.paymentnotification.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_NOT_FOUND("P-001", HttpStatus.NOT_FOUND, "해당 결제 정보를 찾을 수 없습니다."),
    INVALID_PAYMENT_REQUEST("P-002", HttpStatus.BAD_REQUEST, "결제 요청 정보가 올바르지 않습니다."),
    PAYMENT_ALREADY_COMPLETED("P-003", HttpStatus.CONFLICT, "이미 완료된 결제입니다."),
    DUPLICATE_PAYMENT_REQUEST("P-004", HttpStatus.CONFLICT, "이미 처리된 결제 요청입니다."),
    PAYMENT_AMOUNT_MISMATCH("P-005", HttpStatus.BAD_REQUEST, "예매 금액과 결제 요청 금액이 일치하지 않습니다."),
    PAYMENT_NOT_ALLOWED("P-006", HttpStatus.CONFLICT, "현재 상태에서는 결제를 진행할 수 없습니다."),
    PAYMENT_APPROVAL_FAILED("P-007", HttpStatus.BAD_GATEWAY, "결제 승인 처리에 실패했습니다."),
    PAYMENT_GATEWAY_TIMEOUT("P-008", HttpStatus.GATEWAY_TIMEOUT, "결제 처리 중 응답 시간이 초과되었습니다."),
    PAYMENT_GATEWAY_UNAVAILABLE("P-009", HttpStatus.SERVICE_UNAVAILABLE, "결제 서비스를 일시적으로 사용할 수 없습니다."),
    PAYMENT_STATUS_CONFIRMATION_REQUIRED("P-010", HttpStatus.CONFLICT, "결제 결과를 확인할 수 없습니다. 상태 확인이 필요합니다."),
    PAYMENT_CANCELLATION_NOT_ALLOWED("P-011", HttpStatus.BAD_REQUEST, "현재 상태에서는 결제를 취소할 수 없습니다."),
    INVALID_REFUND_AMOUNT("P-012", HttpStatus.BAD_REQUEST, "환불 금액이 올바르지 않습니다."),
    PAYMENT_ALREADY_CANCELLED("P-013", HttpStatus.CONFLICT, "이미 취소된 결제입니다."),
    REFUND_NOT_FOUND("P-014", HttpStatus.NOT_FOUND, "해당 환불 정보를 찾을 수 없습니다."),
    PAYMENT_CANCELLATION_FAILED("P-015", HttpStatus.BAD_GATEWAY, "결제 취소 또는 환불 처리에 실패했습니다."),
    DUPLICATE_REFUND_REQUEST("P-016", HttpStatus.CONFLICT, "이미 처리 중이거나 완료된 환불 요청입니다."),
    PAYMENT_INTERNAL_ERROR("P-017", HttpStatus.INTERNAL_SERVER_ERROR, "결제 처리 중 오류가 발생했습니다."),
    PAYMENT_EVENT_SAVE_FAILED("P-018", HttpStatus.INTERNAL_SERVER_ERROR, "결제 이벤트 처리 중 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    PaymentErrorCode(String code, HttpStatus status, String message) {
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
