package com.platform.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rejects known local/demo secrets when {@code spring.profiles.active} contains {@code prod}.
 * OIDC can plug into the same auth boundary later without changing this check.
 */
public final class ProdSecretsGuard {

    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "change-me",
            "local-service-key",
            "local-internal-key",
            "local-demo-auth-key",
            "practice-jwt-secret",
            "minioadmin"
    );

    private ProdSecretsGuard() {
    }

    public static void assertSafe(String profileCsv, String... secretValues) {
        if (profileCsv == null || profileCsv.isBlank()) {
            return;
        }
        boolean prod = false;
        for (String p : profileCsv.split(",")) {
            if ("prod".equalsIgnoreCase(p.trim()) || "production".equalsIgnoreCase(p.trim())) {
                prod = true;
                break;
            }
        }
        if (!prod) {
            return;
        }
        List<String> bad = new ArrayList<>();
        for (String secret : secretValues) {
            if (secret == null || secret.isBlank()) {
                bad.add("(blank)");
                continue;
            }
            String lower = secret.toLowerCase(Locale.ROOT);
            for (String frag : FORBIDDEN_FRAGMENTS) {
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
                    "Refusing to start with prod profile: secrets look unsafe — " + String.join("; ", bad)
                            + ". Set strong PLATFORM_SERVICE_KEY / PLATFORM_INTERNAL_KEY / JWT_SECRET / RABBITMQ_PASSWORD.");
        }
    }
}
