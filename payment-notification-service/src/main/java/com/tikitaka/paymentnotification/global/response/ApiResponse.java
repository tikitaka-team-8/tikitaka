package com.tikitaka.paymentnotification.global.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import java.time.Instant;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        Instant timestamp,
        String code,
        int status,
        String message,
        T data,
        PageMeta meta
) {
    private static final String SUCCESS_CODE = "SUCCESS";
    public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
        return success(status, message, data, null);
    }
    public static <T> ApiResponse<T> success(
            HttpStatus status,
            String message,
            T data,
            PageMeta meta
    ) {
        return new ApiResponse<>(
                Instant.now(),
                SUCCESS_CODE,
                status.value(),
                message,
                data,
                meta
        );
    }
}
