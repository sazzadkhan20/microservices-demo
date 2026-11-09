package com.platform.gateway.session;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Server-side session revocation (guide §4 — administrative session revocation / logout).
 * Keys expire with the JWT so Redis does not retain stale jtis forever.
 */
@Component
public class SessionRevocationStore {

    private static final String JTI_PREFIX = "platform:session:revoked:jti:";
    private static final String EMP_PREFIX = "platform:session:revoked:emp:";

    private final ReactiveStringRedisTemplate redis;

    public SessionRevocationStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> revokeJti(String jti, Duration ttl) {
        if (jti == null || jti.isBlank()) {
            return Mono.empty();
        }
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(12) : ttl;
        return redis.opsForValue()
                .set(JTI_PREFIX + jti, "1", safeTtl)
                .then();
    }

    public Mono<Void> revokeEmployee(Long employeeId, Duration ttl) {
        if (employeeId == null) {
            return Mono.empty();
        }
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(12) : ttl;
        return redis.opsForValue()
                .set(EMP_PREFIX + employeeId, "1", safeTtl)
                .then();
    }

    public Mono<Boolean> isRevoked(String jti, Long employeeId) {
        Mono<Boolean> jtiCheck = (jti == null || jti.isBlank())
                ? Mono.just(false)
                : redis.hasKey(JTI_PREFIX + jti).defaultIfEmpty(false);
        Mono<Boolean> empCheck = (employeeId == null)
                ? Mono.just(false)
                : redis.hasKey(EMP_PREFIX + employeeId).defaultIfEmpty(false);
        return Mono.zip(jtiCheck, empCheck).map(t -> Boolean.TRUE.equals(t.getT1()) || Boolean.TRUE.equals(t.getT2()));
    }
}
