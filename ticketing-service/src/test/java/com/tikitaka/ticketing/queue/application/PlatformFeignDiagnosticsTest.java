package com.tikitaka.ticketing.queue.application;

import feign.Request;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformFeignDiagnosticsTest {
    @Test
    void classifiesNetworkStackWithoutGuessingUnknownTimeouts() {
        var error = new SocketTimeoutException();
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement("sun.nio.ch.NioSocketImpl", "timedFinishConnect", "NioSocketImpl.java", 1)});
        assertEquals("connect_timeout", PlatformFeignDiagnostics.classify(error));
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement("sun.nio.ch.NioSocketImpl", "timedRead", "NioSocketImpl.java", 1)});
        assertEquals("read_timeout", PlatformFeignDiagnostics.classify(error));
        error.setStackTrace(new StackTraceElement[0]);
        assertEquals("timeout_unknown", PlatformFeignDiagnostics.classify(error));
    }

    @Test
    void preservesClientOptionsAndOriginalExceptionWithoutRetry() {
        var registry = new SimpleMeterRegistry();
        var error = new SocketTimeoutException();
        error.setStackTrace(new StackTraceElement[0]);
        var options = new Request.Options();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var client = new PlatformFeignDiagnostics(registry).enrich((feign.Client) (request, actualOptions) -> {
            assertSame(options, actualOptions);
            calls.incrementAndGet();
            throw error;
        });
        assertSame(error, assertThrows(SocketTimeoutException.class, () -> client.execute(null, options)));
        assertEquals(1, calls.get());
        assertEquals(1.0, registry.get("queue.platform.transport").tag("outcome", "timeout_unknown").counter().count());
    }
}
