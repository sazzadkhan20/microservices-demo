package com.platform.hrms.infrastructure.multitenancy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.RedisKeys;
import com.platform.common.redis.RoutingPayload;
import com.platform.common.tenant.TenantPoolLifecycle;
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

/**
 * Resolves per-tenant DataSources: L1 → Redis L2 → Registry L3.
 * Pool lifecycle via {@link TenantPoolLifecycle} (no Caffeine close race).
 */
@Component
public class TenantDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceManager.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HrmsProperties properties;
    private final RestClient registryClient;
    private final TenantPoolLifecycle pools;

    public TenantDataSourceManager(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            HrmsProperties properties,
            RestClient.Builder restClientBuilder) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.registryClient = restClientBuilder.baseUrl(properties.getRegistry().getBaseUrl()).build();
        int maxPools = properties.getCache() != null && properties.getCache().getL1MaxSize() > 0
                ? (int) Math.min(properties.getCache().getL1MaxSize(), Integer.MAX_VALUE)
                : 50;
        this.pools = new TenantPoolLifecycle(maxPools);
    }

    public DataSource resolve(String tenantId) {
        return pools.resolve(tenantId, this::createPoolFor);
    }

    public void invalidate(String tenantId) {
        pools.invalidate(tenantId);
        log.info("Invalidated tenant DataSource for {}", tenantId);
    }

    public void invalidateAll() {
        pools.invalidateAll();
        log.info("Invalidated all tenant DataSources");
    }

    private HikariDataSource createPoolFor(String tenantId) {
        RoutingPayload payload = loadPayload(tenantId);
        if (!payload.isActive()) {
            throw new IllegalStateException("Tenant routing is not ACTIVE: " + tenantId);
        }
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

    private RoutingPayload loadPayload(String tenantId) {
        String json = redis.opsForValue().get(RedisKeys.routingKey(properties.getServiceName(), tenantId));
        if (json != null) {
            return read(json);
        }
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
            // best-effort
        }
        return payload;
    }

    private RoutingPayload read(String json) {
        try {
            return objectMapper.readValue(json, RoutingPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid routing JSON in Redis", e);
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
