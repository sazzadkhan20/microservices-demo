package com.platform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 session JWT for the single-company deployment.
 * Claims: {@code sub}, {@code employee_id} (required), {@code jti} (session id for revocation).
 * Issued and verified only by the API gateway (guide §4: edge validation; OIDC replaces issuance later).
 */
public final class JwtSupport {

    public static final String CLAIM_EMPLOYEE_ID = "employee_id";
    public static final String CLAIM_SUB = "sub";
    public static final String ISSUER = "hrms-gateway";
    public static final String AUDIENCE = "hrms-services";

    private final SecretKey key;

    public JwtSupport(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** Employee session: identity + unique jti for revocation. */
    public String issueToken(String subject, Long employeeId, long ttlSeconds) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required for a session token");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(subject)
                .claim(CLAIM_EMPLOYEE_ID, employeeId)
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

    public Long requireEmployeeId(Claims claims) {
        Long id = employeeIdOrNull(claims);
        if (id == null) {
            throw new IllegalArgumentException("JWT missing required claim: " + CLAIM_EMPLOYEE_ID);
        }
        return id;
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
