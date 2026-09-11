package com.tikitaka.gateway.global.trace;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(new TraceIdHeaderRequest(request, traceId), response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private static final class TraceIdHeaderRequest extends HttpServletRequestWrapper {

        private final String traceId;

        private TraceIdHeaderRequest(HttpServletRequest request, String traceId) {
            super(request);
            this.traceId = traceId;
        }

        @Override
        public String getHeader(String name) {
            if (TRACE_ID_HEADER.equalsIgnoreCase(name)) {
                return traceId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (TRACE_ID_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(Set.of(traceId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new LinkedHashSet<>();
            Enumeration<String> originalHeaderNames = super.getHeaderNames();

            while (originalHeaderNames != null && originalHeaderNames.hasMoreElements()) {
                String headerName = originalHeaderNames.nextElement();
                if (!TRACE_ID_HEADER.equalsIgnoreCase(headerName)) {
                    headerNames.add(headerName);
                }
            }

            headerNames.add(TRACE_ID_HEADER);
            return Collections.enumeration(headerNames);
        }
    }
}
