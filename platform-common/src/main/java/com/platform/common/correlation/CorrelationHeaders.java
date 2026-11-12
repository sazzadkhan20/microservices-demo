package com.platform.common.correlation;

import org.slf4j.MDC;

/**
 * Guide §9: the caller's correlation id (logging MDC key {@code correlationId}) travels on every
 * synchronous service-to-service call as {@code X-Correlation-ID}. Spring-free so plain JDK clients use it.
 */
public final class CorrelationHeaders {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private CorrelationHeaders() {
    }

    /** Current correlation id or {@code null}; never throws (no SLF4J provider → null). */
    public static String current() {
        try {
            String v = MDC.get(MDC_KEY);
            return v == null || v.isBlank() ? null : v;
        } catch (Throwable t) {
            return null;
        }
    }
}
