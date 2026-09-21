package com.tikitaka.ticketing.global.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 유효한_Trace_ID를_MDC와_응답에_그대로_적용한다() throws Exception {
        String traceId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcTraceId = new AtomicReference<>();
        FilterChain filterChain = (wrappedRequest, wrappedResponse) ->
                mdcTraceId.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        filter.doFilter(request, response, filterChain);

        assertThat(mdcTraceId.get()).isEqualTo(traceId);
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(traceId);
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void Trace_ID가_없으면_새로운_UUID를_생성한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> resolvedTraceId = new AtomicReference<>();
        FilterChain filterChain = (wrappedRequest, wrappedResponse) ->
                resolvedTraceId.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        filter.doFilter(request, response, filterChain);

        assertThat(UUID.fromString(resolvedTraceId.get())).isNotNull();
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(resolvedTraceId.get());
    }

    @Test
    void 유효하지_않은_Trace_ID면_새로운_UUID를_생성한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "invalid-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> resolvedTraceId = new AtomicReference<>();
        FilterChain filterChain = (wrappedRequest, wrappedResponse) ->
                resolvedTraceId.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        filter.doFilter(request, response, filterChain);

        assertThat(resolvedTraceId.get()).isNotEqualTo("invalid-trace-id");
        assertThat(UUID.fromString(resolvedTraceId.get())).isNotNull();
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(resolvedTraceId.get());
    }

    @Test
    void Trace_ID_헤더가_여러_개면_새로운_UUID를_생성한다() throws Exception {
        String firstTraceId = UUID.randomUUID().toString();
        String secondTraceId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, firstTraceId);
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, secondTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (wrappedRequest, wrappedResponse) -> { };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isNotEqualTo(firstTraceId)
                .isNotEqualTo(secondTraceId);
    }

    @Test
    void 요청_처리_중_예외가_발생해도_MDC를_정리한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (wrappedRequest, wrappedResponse) -> {
            throw new IllegalStateException("test exception");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
