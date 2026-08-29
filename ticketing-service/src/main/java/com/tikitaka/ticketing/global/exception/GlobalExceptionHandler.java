package com.tikitaka.ticketing.global.exception;
import com.tikitaka.ticketing.global.response.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GLOBAL_ERROR_KEY = "_global";
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception) {
        return createResponse(exception.getErrorCode());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), getMessage(error))
        );
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent(GLOBAL_ERROR_KEY, getMessage(error))
        );
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiErrorResponse.from(errorCode, errors));
    }
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleUnsupportedRequest(Exception exception) {
        log.warn("지원하지 않는 요청입니다.", exception);
        return createResponse(CommonErrorCode.UNSUPPORTED_REQUEST);
    }
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
    private String getMessage(DefaultMessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "입력값이 올바르지 않습니다.";
    }
}
