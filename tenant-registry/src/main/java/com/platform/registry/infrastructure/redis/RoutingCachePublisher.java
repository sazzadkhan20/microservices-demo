package com.platform.registry.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.RedisKeys;
import com.platform.common.redis.InvalidateEvent;
import com.platform.common.redis.RoutingPayload;
import com.platform.registry.domain.TenantRouting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Writes routing keys to Redis L2 and publishes invalidate events for L1 bust.
 */
@Component
public class RoutingCachePublisher {

    private static final Logger log = LoggerFactory.getLogger(RoutingCachePublisher.class);
    private static final Duration ROUTING_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RoutingCachePublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publishRouting(TenantRouting routing) {
        RoutingPayload payload = new RoutingPayload(
                routing.getJdbcUrl(),
                routing.getUsername(),
                routing.getPassword(),
                routing.getStatus(),
                routing.getVersion()
        );
        try {
            String key = RedisKeys.routingKey(routing.getServiceName(), routing.getTenantId());
            redis.opsForValue().set(key, objectMapper.writeValueAsString(payload), ROUTING_TTL);

            String versionKey = RedisKeys.versionKey(routing.getServiceName());
            redis.opsForValue().set(versionKey, String.valueOf(routing.getVersion()));

            InvalidateEvent event = new InvalidateEvent(
                    routing.getServiceName(),
                    routing.getTenantId(),
                    routing.getVersion()
            );
            redis.convertAndSend(RedisKeys.INVALIDATE_CHANNEL, objectMapper.writeValueAsString(event));
            log.info("Published routing + invalidate for {} / {} v{}",
                    routing.getServiceName(), routing.getTenantId(), routing.getVersion());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize routing payload", e);
        }
    }
}
