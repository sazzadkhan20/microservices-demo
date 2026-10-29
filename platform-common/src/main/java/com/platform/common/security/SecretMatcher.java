package com.platform.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time comparison of shared secrets (service key, demo-auth key).
 */
public final class SecretMatcher {

    private SecretMatcher() {
    }

    /**
     * @return true only when both values are non-blank and equal (constant time on the digest).
     */
    public static boolean matches(String provided, String expected) {
        if (provided == null || expected == null || provided.isBlank() || expected.isBlank()) {
            return false;
        }
        // Compare fixed-length digests so length differences do not leak via timing.
        return MessageDigest.isEqual(sha256(provided), sha256(expected));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
