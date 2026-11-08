package com.platform.messaging.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.RedisKeys;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Discovers tenant ids that have <strong>ACTIVE</strong> routing cached for a service (Redis L2).
 * Used by outbox schedulers so PENDING rows drain after restart without waiting
 * for an HTTP request to open a pool — and without hammering SCHEMA_OUTDATED / inactive rows.
 */
public final class RoutedTenantScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RoutedTenantScanner() {
    }

    public static Set<String> listTenantIds(StringRedisTemplate redis, String serviceName) {
        if (redis == null || serviceName == null || serviceName.isBlank()) {
            return Set.of();
        }
        String prefix = RedisKeys.ROUTING_PREFIX + serviceName.trim() + ":";
        Set<String> tenants = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(100).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (key == null || !key.startsWith(prefix) || key.length() <= prefix.length()) {
                    continue;
                }
                String tenantId = key.substring(prefix.length());
                if (isActiveRouting(redis, key)) {
                    tenants.add(tenantId);
                }
            }
        } catch (Exception ignored) {
            // Scheduler must not crash the process if Redis is briefly unavailable.
        }
        return tenants;
    }

    private static boolean isActiveRouting(StringRedisTemplate redis, String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return false;
            }
            JsonNode node = MAPPER.readTree(json);
            JsonNode status = node.get("status");
            return status != null && "ACTIVE".equalsIgnoreCase(status.asText());
        } catch (Exception e) {
            return false;
        }
    }
}
