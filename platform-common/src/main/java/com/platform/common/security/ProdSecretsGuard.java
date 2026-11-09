package com.platform.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rejects unsafe local/demo configuration when running as production
 * (guide §9 / §14 — startup rejection of development credentials).
 */
public final class ProdSecretsGuard {

    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "change-me",
            "local-service-key",
            "local-internal-key",
            "local-demo-auth-key",
            "local-platform-admin",
            "local-dev-jwt",
            "practice-jwt-secret",
            "minioadmin",
            "platform" // default local broker/db password reused as literal in many envs
    );

    private ProdSecretsGuard() {
    }

    public static boolean isProd(String profileCsv, String platformEnv) {
        if (platformEnv != null && "prod".equalsIgnoreCase(platformEnv.trim())) {
            return true;
        }
        if (platformEnv != null && "production".equalsIgnoreCase(platformEnv.trim())) {
            return true;
        }
        if (profileCsv == null || profileCsv.isBlank()) {
            return false;
        }
        for (String p : profileCsv.split(",")) {
            String t = p.trim();
            if ("prod".equalsIgnoreCase(t) || "production".equalsIgnoreCase(t)) {
                return true;
            }
        }
        return false;
    }

    public static void assertSafe(String profileCsv, String... secretValues) {
        assertSafeWithEnv(profileCsv, System.getenv("PLATFORM_ENV"), secretValues);
    }

    public static void assertSafeWithEnv(String profileCsv, String platformEnv, String... secretValues) {
        assertSafe(profileCsv, platformEnv, secretValues);
    }

    private static void assertSafe(String profileCsv, String platformEnv, String[] secretValues) {
        if (!isProd(profileCsv, platformEnv)) {
            return;
        }
        List<String> bad = new ArrayList<>();
        for (String secret : secretValues) {
            if (secret == null || secret.isBlank()) {
                bad.add("(blank)");
                continue;
            }
            String lower = secret.toLowerCase(Locale.ROOT);
            // Exact default local password "platform" is unsafe in prod; fragment "platform" alone
            // would false-positive on hostnames — check exact/common defaults instead.
            if ("platform".equals(lower) || "postgres".equals(lower) || "minioadmin".equals(lower)) {
                bad.add("default credential '" + secret + "'");
                continue;
            }
            for (String frag : FORBIDDEN_FRAGMENTS) {
                if ("platform".equals(frag)) {
                    continue;
                }
                if (lower.contains(frag)) {
                    bad.add("contains '" + frag + "'");
                    break;
                }
            }
            if (secret.length() < 24) {
                bad.add("too short (<24)");
            }
        }
        if (!bad.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start in production: secrets look unsafe — " + String.join("; ", bad)
                            + ". Set strong PLATFORM_SERVICE_KEY / PLATFORM_INTERNAL_KEY / JWT_SECRET / "
                            + "RABBITMQ_PASSWORD / PLATFORM_ADMIN_KEY and disable demo auth.");
        }
    }

    /** SEC-02 / SEC-07 — refuse demo auth or open gateway in production. */
    public static void assertAuthFlags(String profileCsv, String platformEnv,
                                       boolean demoAuthEnabled, boolean requireGateway) {
        if (!isProd(profileCsv, platformEnv)) {
            return;
        }
        if (demoAuthEnabled) {
            throw new IllegalStateException(
                    "Refusing to start in production: demo-auth must be disabled (SEC-02).");
        }
        if (!requireGateway) {
            throw new IllegalStateException(
                    "Refusing to start in production: platform.security.require-gateway must be true (SEC-07).");
        }
    }
}
