package com.platform.common.correlation;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SEC-18 — accept only opaque correlation ids; anything else is replaced with a new UUID.
 */
public final class CorrelationIds {

    public static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private CorrelationIds() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static String sanitizeOrCreate(String incoming) {
        if (incoming == null) {
            return UUID.randomUUID().toString();
        }
        String trimmed = incoming.trim();
        if (isValid(trimmed)) {
            return trimmed;
        }
        return UUID.randomUUID().toString();
    }
}
