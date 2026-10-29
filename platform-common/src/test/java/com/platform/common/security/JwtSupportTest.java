package com.platform.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSupportTest {

    private final JwtSupport jwt = new JwtSupport("practice-jwt-secret-change-me-32chars-min");

    @Test
    void roundtrip_tenant_and_employee_claims() {
        String token = jwt.issueToken("emp-1", "qii_creative", 1L, 3600);
        Claims c = jwt.parseAndValidate(token);
        assertEquals("qii_creative", jwt.requireTenantId(c));
        assertEquals(1L, jwt.employeeIdOrNull(c));
    }

    @Test
    void employee_claim_optional() {
        String token = jwt.issueToken("anon", "techshop_retail", 60);
        Claims c = jwt.parseAndValidate(token);
        assertNull(jwt.employeeIdOrNull(c));
    }

    @Test
    void rejects_short_secret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtSupport("too-short"));
    }
}
