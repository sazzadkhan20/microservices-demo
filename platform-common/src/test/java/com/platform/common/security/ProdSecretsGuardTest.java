package com.platform.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdSecretsGuardTest {

    @Test
    void assertAuthFlagsAllowsSafeProd() {
        assertDoesNotThrow(() -> ProdSecretsGuard.assertAuthFlags("prod", null, false, true));
        assertDoesNotThrow(() -> ProdSecretsGuard.assertAuthFlags("prod", "prod", false, true));
    }

    @Test
    void assertAuthFlagsRejectsDemoAuthInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ProdSecretsGuard.assertAuthFlags("prod", null, true, true));
        assertTrue(ex.getMessage().contains("demo-auth"));
    }

    @Test
    void assertAuthFlagsRejectsOpenGatewayInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ProdSecretsGuard.assertAuthFlags("production", "prod", false, false));
        assertTrue(ex.getMessage().contains("require-gateway"));
    }

    @Test
    void assertAuthFlagsIsNoopOutsideProd() {
        assertDoesNotThrow(() -> ProdSecretsGuard.assertAuthFlags("dev", null, true, false));
        assertDoesNotThrow(() -> ProdSecretsGuard.assertAuthFlags("", "staging", true, false));
        assertFalse(ProdSecretsGuard.isProd("local", "dev"));
        assertTrue(ProdSecretsGuard.isProd("prod,metrics", null));
    }
}
