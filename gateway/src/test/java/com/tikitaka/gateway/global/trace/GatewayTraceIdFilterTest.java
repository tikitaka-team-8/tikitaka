package com.tikitaka.gateway.global.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayTraceIdFilterTest {

    private final GatewayTraceIdFilter filter = new GatewayTraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 외부_Trace_ID를_새로운_UUID로_교체하고_MDC와_응답에_적용한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayTraceIdFilter.TRACE_ID_HEADER, "external-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> forwardedTraceId = new AtomicReference<>();
        AtomicReference<String> mdcTraceId = new AtomicReference<>();
        AtomicReference<Integer> forwardedHeaderCount = new AtomicReference<>();

        FilterChain filterChain = (wrappedRequest, wrappedResponse) -> {
            forwardedTraceId.set(((jakarta.servlet.http.HttpServletRequest) wrappedRequest)
                    .getHeader(GatewayTraceIdFilter.TRACE_ID_HEADER));
            mdcTraceId.set(MDC.get(GatewayTraceIdFilter.TRACE_ID_MDC_KEY));
            forwardedHeaderCount.set(Collections.list(
                    ((jakarta.servlet.http.HttpServletRequest) wrappedRequest)
                            .getHeaders(GatewayTraceIdFilter.TRACE_ID_HEADER)
            ).size());
        };

        filter.doFilter(request, response, filterChain);

        assertThat(forwardedTraceId.get()).isNotEqualTo("external-trace-id");
        assertThat(UUID.fromString(forwardedTraceId.get())).isNotNull();
        assertThat(forwardedHeaderCount.get()).isEqualTo(1);
        assertThat(mdcTraceId.get()).isEqualTo(forwardedTraceId.get());
        assertThat(response.getHeader(GatewayTraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(forwardedTraceId.get());
        assertThat(MDC.get(GatewayTraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void 요청_처리_중_예외가_발생해도_MDC를_정리한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (wrappedRequest, wrappedResponse) -> {
            throw new IllegalStateException("test exception");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(GatewayTraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
