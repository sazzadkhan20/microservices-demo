package com.platform.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSupportTest {

    private final JwtSupport jwt = new JwtSupport("practice-jwt-secret-change-me-32chars-min");

    @Test
    void roundtrip_employee_claims() {
        String token = jwt.issueToken("emp-1", 1L, 3600);
        Claims c = jwt.parseAndValidate(token);
        assertEquals(1L, jwt.requireEmployeeId(c));
        assertNull(c.get("tenant_id"));
        assertEquals(1L, jwt.employeeIdOrNull(c));
        assertEquals("emp-1", c.getSubject());
        org.junit.jupiter.api.Assertions.assertFalse(jwt.requireJti(c).isBlank());
    }

    @Test
    void employee_claim_required_on_issue() {
        assertThrows(IllegalArgumentException.class, () -> jwt.issueToken("anon", null, 60));
    }

    @Test
    void rejects_short_secret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtSupport("too-short"));
    }
}
