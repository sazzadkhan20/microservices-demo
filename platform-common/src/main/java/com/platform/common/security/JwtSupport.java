package com.platform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * HS256 JWT helper. Claims: tenant_id (required), employee_id (optional for demo picker / product auth).
 */
public final class JwtSupport {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_EMPLOYEE_ID = "employee_id";
    public static final String CLAIM_SUB = "sub";

    private final SecretKey key;

    public JwtSupport(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** Legacy practice token (tenant only). */
    public String issueToken(String subject, String tenantId, long ttlSeconds) {
        return issueToken(subject, tenantId, null, ttlSeconds);
    }

    /** Product / demo session: tenant + employee identity. */
    public String issueToken(String subject, String tenantId, Long employeeId, long ttlSeconds) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TENANT_ID, tenantId);
        if (employeeId != null) {
            claims.put(CLAIM_EMPLOYEE_ID, employeeId);
        }
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String requireTenantId(Claims claims) {
        Object value = claims.get(CLAIM_TENANT_ID);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("JWT missing required claim: " + CLAIM_TENANT_ID);
        }
        return value.toString();
    }

    public Long employeeIdOrNull(Claims claims) {
        Object value = claims.get(CLAIM_EMPLOYEE_ID);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
