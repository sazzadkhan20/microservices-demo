package com.platform.messaging.correlation;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Current correlation id (HTTP request or consumed message). Mirrored into the logging MDC under
 * {@link #MDC_KEY} so every log line carries it (guide §9: propagate correlation IDs).
 */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
        if (correlationId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, correlationId);
        }
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String getOrCreate() {
        String existing = CURRENT.get();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String created = UUID.randomUUID().toString();
        set(created);
        return created;
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }
}
