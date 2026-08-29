package com.tikitaka.ticketing.global.exception;
import org.springframework.http.HttpStatus;
public interface ErrorCode {
    String getCode();
    HttpStatus getStatus();
    String getMessage();
}
