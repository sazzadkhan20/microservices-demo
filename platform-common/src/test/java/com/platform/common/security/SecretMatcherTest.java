package com.platform.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMatcherTest {

    @Test
    void matchesEqualSecrets() {
        assertTrue(SecretMatcher.matches("abc", "abc"));
    }

    @Test
    void rejectsMissingBlankOrDifferent() {
        assertFalse(SecretMatcher.matches(null, "abc"));
        assertFalse(SecretMatcher.matches("abc", null));
        assertFalse(SecretMatcher.matches("", ""));
        assertFalse(SecretMatcher.matches("  ", "  "));
        assertFalse(SecretMatcher.matches("abc", "abd"));
        assertFalse(SecretMatcher.matches("abc", "abcd"));
    }
}
