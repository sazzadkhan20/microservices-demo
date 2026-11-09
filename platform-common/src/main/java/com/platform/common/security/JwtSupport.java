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
import java.util.UUID;

/**
 * HS256 JWT helper. Claims: tenant_id (required), employee_id (optional), jti (session id).
 * Guide §4 (OIDC deferred): gateway validates signature/exp/alg; jti enables session revocation.
 */
public final class JwtSupport {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_EMPLOYEE_ID = "employee_id";
    public static final String CLAIM_SUB = "sub";
    public static final String ISSUER = "platform-registry";
    public static final String AUDIENCE = "platform-services";

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

    /** Product / demo session: tenant + employee identity + unique jti for revocation. */
    public String issueToken(String subject, String tenantId, Long employeeId, long ttlSeconds) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TENANT_ID, tenantId);
        if (employeeId != null) {
            claims.put(CLAIM_EMPLOYEE_ID, employeeId);
        }
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("JWT missing required claim: exp");
        }
        if (claims.getId() == null || claims.getId().isBlank()) {
            throw new IllegalArgumentException("JWT missing required claim: jti");
        }
        return claims;
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

    public String requireJti(Claims claims) {
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("JWT missing required claim: jti");
        }
        return jti;
    }
}
