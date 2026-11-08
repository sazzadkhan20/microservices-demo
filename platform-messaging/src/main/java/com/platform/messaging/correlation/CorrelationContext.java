package com.platform.messaging.correlation;

import java.util.UUID;

public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
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
        CURRENT.set(created);
        return created;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
