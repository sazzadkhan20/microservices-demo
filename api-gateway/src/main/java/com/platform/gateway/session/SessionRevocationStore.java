package com.platform.gateway.session;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Server-side session revocation (guide §4 — administrative session revocation / logout).
 * Keys expire with the JWT so Redis does not retain stale jtis forever.
 *
 * <p>Employee-wide revocation stores the revocation instant (epoch seconds): every session issued
 * at or before it is refused, while a session minted later (after a legitimate re-activation and
 * new login) is accepted. Legacy entries written as {@code "1"} keep their old meaning (revoke all).
 */
@Component
public class SessionRevocationStore {

    private static final String JTI_PREFIX = "platform:session:revoked:jti:";
    private static final String EMP_PREFIX = "platform:session:revoked:emp:";
    private static final String LEGACY_REVOKE_ALL = "1";

    private final ReactiveStringRedisTemplate redis;

    public SessionRevocationStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> revokeJti(String jti, Duration ttl) {
        if (jti == null || jti.isBlank()) {
            return Mono.empty();
        }
        return redis.opsForValue()
                .set(JTI_PREFIX + jti, "1", safeTtl(ttl))
                .then();
    }

    public Mono<Void> revokeEmployee(Long employeeId, Duration ttl) {
        if (employeeId == null) {
            return Mono.empty();
        }
        return redis.opsForValue()
                .set(EMP_PREFIX + employeeId, String.valueOf(Instant.now().getEpochSecond()), safeTtl(ttl))
                .then();
    }

    /** @param issuedAtEpochSeconds JWT {@code iat}; {@code null} is treated as "issued before any revocation". */
    public Mono<Boolean> isRevoked(String jti, Long employeeId, Long issuedAtEpochSeconds) {
        Mono<Boolean> jtiCheck = (jti == null || jti.isBlank())
                ? Mono.just(false)
                : redis.hasKey(JTI_PREFIX + jti).defaultIfEmpty(false);
        Mono<Boolean> empCheck = (employeeId == null)
                ? Mono.just(false)
                : redis.opsForValue().get(EMP_PREFIX + employeeId)
                        .map(v -> revokedByEmployeeEntry(v, issuedAtEpochSeconds))
                        .defaultIfEmpty(false);
        return Mono.zip(jtiCheck, empCheck).map(t -> Boolean.TRUE.equals(t.getT1()) || Boolean.TRUE.equals(t.getT2()));
    }

    /** Back-compat overload (no iat): any employee-wide entry revokes. */
    public Mono<Boolean> isRevoked(String jti, Long employeeId) {
        return isRevoked(jti, employeeId, null);
    }

    static boolean revokedByEmployeeEntry(String value, Long issuedAtEpochSeconds) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.isEmpty() || LEGACY_REVOKE_ALL.equals(v) || issuedAtEpochSeconds == null) {
            return true;
        }
        try {
            return issuedAtEpochSeconds <= Long.parseLong(v);
        } catch (NumberFormatException e) {
            return true; // unknown format: fail closed
        }
    }

    private static Duration safeTtl(Duration ttl) {
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(12) : ttl;
    }
}
