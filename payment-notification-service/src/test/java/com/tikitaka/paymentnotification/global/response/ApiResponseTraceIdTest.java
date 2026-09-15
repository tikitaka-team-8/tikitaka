package com.tikitaka.paymentnotification.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.tikitaka.paymentnotification.global.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

class ApiResponseTraceIdTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 성공_응답에_현재_Trace_ID를_포함한다() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        ApiResponse<Void> response = ApiResponse.success(HttpStatus.OK, "성공", null);

        assertThat(response.traceId()).isEqualTo(traceId);
    }

    @Test
    void 실패_응답에_현재_Trace_ID를_포함한다() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        ApiErrorResponse response = ApiErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(response.traceId()).isEqualTo(traceId);
    }
}
