package com.tikitaka.ticketing.global.exception;

import com.tikitaka.ticketing.global.response.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GLOBAL_ERROR_KEY = "_global";

    // 비즈니스 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception) {
        log.warn("비즈니스 예외 발생: code={}, message={}", exception.getErrorCode().getCode(), exception.getMessage());
        return createResponse(exception.getErrorCode());
    }

    // 요청 객체 Validation 오류 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), getMessage(error))
        );
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent(GLOBAL_ERROR_KEY, getMessage(error))
        );

        return createValidationResponse(errors);
    }

    // 잘못된 JSON 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return createValidationResponse(Map.of(
                GLOBAL_ERROR_KEY,
                "요청 본문 형식이 올바르지 않습니다."
        ));
    }

    // 필수 Query Parameter 누락 처리
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception
    ) {
        return createValidationResponse(Map.of(
                exception.getParameterName(),
                "필수 요청 파라미터입니다."
        ));
    }

    // 필수 Header 누락 처리
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        return createValidationResponse(Map.of(
                exception.getHeaderName(),
                "필수 요청 헤더입니다."
        ));
    }

    // Query Parameter 및 Path Variable 타입 오류 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return createValidationResponse(Map.of(
                exception.getName(),
                "요청값의 형식이 올바르지 않습니다."
        ));
    }

    // 메서드 파라미터 Validation 오류 처리
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String errorKey = parameterName != null ? parameterName : GLOBAL_ERROR_KEY;
            result.getResolvableErrors().forEach(error ->
                    errors.putIfAbsent(errorKey, getMessage(error))
            );
        });
        exception.getCrossParameterValidationResults().forEach(error ->
                errors.putIfAbsent(GLOBAL_ERROR_KEY, getMessage(error))
        );

        return createValidationResponse(errors);
    }

    // 지원하지 않는 HTTP 요청 처리
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleUnsupportedRequest(Exception exception) {
        log.warn("지원하지 않는 요청입니다.", exception);
        return createResponse(CommonErrorCode.UNSUPPORTED_REQUEST);
    }

    // 클라이언트가 응답을 받기 전에 연결을 끊은 경우 (타임아웃/중도 취소 등).
    // 이미 커넥션이 끊어진 상태라 응답을 다시 쓸 수 없으므로, 여기서 잡아서 조용히 로그만 남기고
    // 아래 handleUnexpectedException(500 처리)로 흘러가 노이즈성 에러 로그가 남는 것을 막는다.
    // (부하테스트처럼 클라이언트 타임아웃이 잦은 상황에서 응답 생성이 느려질 때 자주 발생 - 실제 버그가 아님)
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        log.warn("클라이언트가 응답 수신 전 연결을 종료했습니다(타임아웃/취소 등): {}", exception.getMessage());
    }

    // 예상하지 못한 서버 오류 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("예상하지 못한 서버 오류가 발생했습니다.", exception);
        return createResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiErrorResponse> createResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiErrorResponse.from(errorCode));
    }

    private ResponseEntity<ApiErrorResponse> createValidationResponse(Map<String, String> errors) {
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiErrorResponse.from(errorCode, errors));
    }

    private String getMessage(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "입력값이 올바르지 않습니다.";
    }
}
