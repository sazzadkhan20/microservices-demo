package com.platform.common.security;

/**
 * Credential for <b>system-originated</b> service-to-service calls that act on behalf of nobody
 * (e.g. HRMS pushing a "leave approved" notification to another employee).
 *
 * <p>This is deliberately NOT {@code X-Service-Key}: the Gateway stamps that key on every proxied
 * user request, so it proves "came through the gateway", not "is a backend service".
 * {@link #HEADER} is never set by the Gateway; only backend services holding the shared
 * {@code platform.security.internal-key} secret can present it.
 */
public final class InternalCalls {

    public static final String HEADER = "X-Internal-Key";
    public static final String DEFAULT_DEV_KEY = "local-internal-key-change-me";

    private InternalCalls() {
    }

    public static boolean isTrusted(String providedHeaderValue, String configuredKey) {
        return SecretMatcher.matches(providedHeaderValue, configuredKey);
    }
}
