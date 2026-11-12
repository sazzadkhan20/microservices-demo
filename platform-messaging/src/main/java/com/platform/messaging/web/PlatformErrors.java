package com.platform.messaging.web;

import com.platform.messaging.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error bodies shared by every service (guide §7 common error envelope). The body keeps the
 * historical {@code error} key that the BFF/frontend reads and adds {@code status}, {@code code},
 * {@code message} and {@code correlationId}. Internal exception text never leaves the service:
 * it is logged with the correlation id instead (guide §9: no internals in responses).
 */
public final class PlatformErrors {

    private static final Logger log = LoggerFactory.getLogger(PlatformErrors.class);

    private PlatformErrors() {
    }

    public static Map<String, String> body(int status, String code, String message) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("error", message);
        out.put("status", String.valueOf(status));
        out.put("code", code);
        out.put("message", message);
        out.put("correlationId", CorrelationContext.getOrCreate());
        return out;
    }

    public static ResponseEntity<Map<String, String>> of(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(body(status.value(), code, message));
    }

    /** Unexpected failure: log the full exception, return only a reference to the caller. */
    public static ResponseEntity<Map<String, String>> unexpected(Exception ex) {
        String cid = CorrelationContext.getOrCreate();
        log.error("Unhandled error [correlationId={}]", cid, ex);
        return of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error — reference " + cid);
    }
}
