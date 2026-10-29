package com.platform.hrms.infrastructure.multitenancy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.common.constants.RedisKeys;
import com.platform.common.redis.RoutingPayload;
import com.platform.hrms.config.HrmsProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves per-tenant DataSources: Caffeine L1 → Redis L2 → Registry L3.
 *
 * <p>Also closes pools on invalidate / shutdown to avoid connection leaks.
 */
@Component
public class TenantDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceManager.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HrmsProperties properties;
    private final RestClient registryClient;
    private final Cache<String, HikariDataSource> l1;
    private final Map<String, HikariDataSource> allPools = new ConcurrentHashMap<>();

    public TenantDataSourceManager(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            HrmsProperties properties,
            RestClient.Builder restClientBuilder) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.registryClient = restClientBuilder.baseUrl(properties.getRegistry().getBaseUrl()).build();
        this.l1 = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getL1MaxSize())
                .expireAfterAccess(Duration.ofMinutes(properties.getCache().getL1ExpireAfterAccessMinutes()))
                .removalListener((key, value, cause) -> {
                    // When Caffeine evicts, close the pool if still mapped
                    if (key != null) {
                        closeQuietly(allPools.remove(key.toString()));
                    }
                })
                .build();
    }

    public DataSource resolve(String tenantId) {
        HikariDataSource cached = l1.getIfPresent(tenantId);
        if (cached != null && !cached.isClosed()) {
            return cached;
        }

        RoutingPayload payload = loadPayload(tenantId);
        if (!payload.isActive()) {
            throw new IllegalStateException("Tenant routing is not ACTIVE: " + tenantId);
        }

        HikariDataSource ds = createPool(tenantId, payload);
        allPools.put(tenantId, ds);
        l1.put(tenantId, ds);
        return ds;
    }

    public void invalidate(String tenantId) {
        l1.invalidate(tenantId);
        closeQuietly(allPools.remove(tenantId));
        log.info("Invalidated tenant DataSource L1 for {}", tenantId);
    }

    public void invalidateAll() {
        l1.invalidateAll();
        allPools.keySet().forEach(this::invalidate);
    }

    private RoutingPayload loadPayload(String tenantId) {
        // L2 Redis
        String json = redis.opsForValue().get(RedisKeys.routingKey(properties.getServiceName(), tenantId));
        if (json != null) {
            return read(json);
        }

        // L3 Registry (includes credentials)
        log.warn("Redis L2 miss for tenant {} — falling back to Registry L3", tenantId);
        InternalRouting body = registryClient.get()
                .uri("/api/registry/internal/routing/{service}/{tenantId}", properties.getServiceName(), tenantId)
                .header("X-Service-Key", properties.getRegistry().getServiceKey())
                .retrieve()
                .body(InternalRouting.class);
        if (body == null) {
            throw new IllegalStateException("No routing found for tenant " + tenantId);
        }
        RoutingPayload payload = new RoutingPayload(
                body.jdbcUrl(), body.username(), body.password(), body.status(), body.version());
        try {
            redis.opsForValue().set(
                    RedisKeys.routingKey(properties.getServiceName(), tenantId),
                    objectMapper.writeValueAsString(payload),
                    Duration.ofHours(24));
        } catch (Exception ignored) {
            // cache fill is best-effort
        }
        return payload;
    }

    private HikariDataSource createPool(String tenantId, RoutingPayload payload) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("hrms-" + tenantId);
        config.setJdbcUrl(payload.jdbcUrl());
        config.setUsername(payload.username());
        config.setPassword(payload.password());
        config.setMaximumPoolSize(properties.getDatasource().getMaximumPoolSize());
        config.setMinimumIdle(properties.getDatasource().getMinimumIdle());
        config.setMaxLifetime(properties.getDatasource().getMaxLifetimeMs());
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }

    private RoutingPayload read(String json) {
        try {
            return objectMapper.readValue(json, RoutingPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid routing JSON in Redis", e);
        }
    }

    private void closeQuietly(HikariDataSource ds) {
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    @PreDestroy
    public void shutdown() {
        invalidateAll();
    }

    public record InternalRouting(
            String serviceName,
            String tenantId,
            String jdbcUrl,
            String username,
            String password,
            String status,
            long version
    ) {
    }
}
