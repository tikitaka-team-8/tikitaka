package com.tikitaka.ticketing.queue.exception;

import com.tikitaka.ticketing.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum QueueErrorCode implements ErrorCode {
    QUEUE_ACCESS_DENIED("Q-001", HttpStatus.FORBIDDEN, "대기열 입장 권한이 없습니다."),
    QUEUE_ENTRY_NOT_FOUND("Q-002", HttpStatus.NOT_FOUND, "대기열 참여 정보를 찾을 수 없습니다."),
    QUEUE_EXIT_NOT_ALLOWED("Q-003", HttpStatus.CONFLICT, "현재 상태에서는 대기열에서 이탈할 수 없습니다."),
    QUEUE_SERVICE_UNAVAILABLE("Q-004", HttpStatus.SERVICE_UNAVAILABLE, "대기열 서비스를 일시적으로 이용할 수 없습니다."),
    QUEUE_SESSION_NOT_FOUND("Q-005", HttpStatus.NOT_FOUND, "공연 회차를 찾을 수 없습니다."),
    QUEUE_SESSION_NOT_OPEN("Q-006", HttpStatus.CONFLICT, "현재 회차는 대기열 진입이 불가능합니다."),
    QUEUE_ENTRY_STATE_CONFLICT("Q-007", HttpStatus.CONFLICT, "현재 대기열 상태에서는 요청을 처리할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    QueueErrorCode(String code, HttpStatus status, String message) {
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
