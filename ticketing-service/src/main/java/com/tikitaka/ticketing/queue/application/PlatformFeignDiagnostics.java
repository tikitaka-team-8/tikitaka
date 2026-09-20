package com.tikitaka.ticketing.queue.application;

import feign.Capability;
import feign.Client;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only observation: delegates unchanged and never logs headers, URLs or response bodies. */
public class PlatformFeignDiagnostics implements Capability {
    private static final Logger log = LoggerFactory.getLogger(PlatformFeignDiagnostics.class);
    private final MeterRegistry registry;

    public PlatformFeignDiagnostics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Client enrich(Client delegate) {
        AtomicBoolean first = new AtomicBoolean(true);
        registry.counter("queue.platform.client", "implementation", delegate.getClass().getName()).increment();
        for (String outcome : new String[]{"response", "connect_timeout", "read_timeout", "timeout_unknown", "io_error"}) {
            registry.counter("queue.platform.transport", "outcome", outcome);
        }
        return (request, options) -> {
            if (first.compareAndSet(true, false)) {
                log.info("QUEUE_PLATFORM_CLIENT implementation={} connectTimeoutMs={} readTimeoutMs={}",
                        delegate.getClass().getName(), options.connectTimeoutMillis(), options.readTimeoutMillis());
            }
            try {
                var response = delegate.execute(request, options);
                registry.counter("queue.platform.transport", "outcome", "response").increment();
                return response;
            } catch (IOException exception) {
                String outcome = classify(exception);
                registry.counter("queue.platform.transport", "outcome", outcome).increment();
                // Counters collect every failure without adding synchronous log output per failed request.
                throw exception;
            }
        };
    }

    static String classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (!(cause instanceof SocketTimeoutException)) continue;
            for (StackTraceElement frame : cause.getStackTrace()) {
                String owner = frame.getClassName();
                if (!(owner.startsWith("java.net.") || owner.startsWith("sun.nio.ch.")
                        || owner.startsWith("sun.net."))) continue;
                String method = frame.getMethodName().toLowerCase(java.util.Locale.ROOT);
                if (method.contains("connect")) return "connect_timeout";
                if (method.contains("read")) return "read_timeout";
            }
            return "timeout_unknown";
        }
        return "io_error";
    }
}
