package com.platform.gateway.oidc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdTokenVerifierTest {

    @Test
    void allowsConfiguredDomains() {
        assertDoesNotThrow(() ->
                GoogleIdTokenVerifier.assertAllowedDomain(
                        "sazzad53913@gmail.com",
                        "qiicreative.com.au,gmail.com"));
        assertDoesNotThrow(() ->
                GoogleIdTokenVerifier.assertAllowedDomain(
                        "hr@qiicreative.com.au",
                        "qiicreative.com.au"));
    }

    @Test
    void rejectsOtherDomains() {
        assertThrows(IllegalArgumentException.class, () ->
                GoogleIdTokenVerifier.assertAllowedDomain(
                        "outsider@example.com",
                        "qiicreative.com.au,gmail.com"));
    }
}
