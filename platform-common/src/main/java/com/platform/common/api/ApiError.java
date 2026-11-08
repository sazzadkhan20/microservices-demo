package com.platform.common.api;

import java.time.Instant;
import java.util.Map;

/**
 * Common API error envelope (guide §7) — includes {@code code} + {@code correlationId}.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, Object> details
) {
    public static ApiError of(int status, String error, String message, String path) {
        return of(status, error, error, message, path, null, Map.of());
    }

    public static ApiError of(int status, String error, String message, String path, Map<String, Object> details) {
        return of(status, error, error, message, path, null, details);
    }

    public static ApiError of(
            int status,
            String error,
            String code,
            String message,
            String path,
            String correlationId,
            Map<String, Object> details) {
        return new ApiError(
                Instant.now(),
                status,
                error,
                code == null ? error : code,
                message,
                path,
                correlationId,
                details == null ? Map.of() : details);
    }
}
