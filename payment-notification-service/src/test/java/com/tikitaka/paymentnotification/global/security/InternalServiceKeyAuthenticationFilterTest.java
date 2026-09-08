package com.tikitaka.paymentnotification.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalServiceKeyAuthenticationFilterTest {

    private static final String SERVICE_KEY = "test-internal-service-key";

    private final InternalServiceKeyAuthenticationFilter filter =
            new InternalServiceKeyAuthenticationFilter(SERVICE_KEY);

    @Test
    void 올바른_서비스_키이면_내부_API_요청을_허용한다() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-Key", SERVICE_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.invoked).isTrue();
    }

    @Test
    void 서비스_키가_누락되면_내부_API_요청을_거부한다() throws Exception {
        MockHttpServletRequest request = internalRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(filterChain.invoked).isFalse();
    }

    @Test
    void 서비스_키가_일치하지_않으면_내부_API_요청을_거부한다() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-Key", "invalid-service-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(filterChain.invoked).isFalse();
    }

    @Test
    void 서비스_키가_여러_개이면_내부_API_요청을_거부한다() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-Key", SERVICE_KEY);
        request.addHeader("X-Service-Key", SERVICE_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(filterChain.invoked).isFalse();
    }

    @Test
    void 외부_API에는_서비스_키_검증을_적용하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments/payment-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.invoked).isTrue();
    }

    private MockHttpServletRequest internalRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/internal/payments");
    }

    private static final class RecordingFilterChain implements FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }
}
