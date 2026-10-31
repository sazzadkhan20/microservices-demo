package com.platform.registry.infrastructure.provision;

import com.platform.common.constants.RedisKeys;
import com.platform.registry.application.ProvisioningService;
import com.platform.registry.config.PlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Large-SaaS fleet schema rollout: on registry boot, apply shared Liquibase changelogs
 * to every tenant DB so old and new businesses stay on the same product schema.
 *
 * <p>Leader election via Redis SET NX. Readiness stays REFUSING_TRAFFIC until done
 * so orchestrators do not send traffic mid-migrate.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FleetSchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FleetSchemaMigrationRunner.class);

    private final ProvisioningService provisioningService;
    private final PlatformProperties properties;
    private final StringRedisTemplate redis;
    private final ApplicationContext applicationContext;

    public FleetSchemaMigrationRunner(
            ProvisioningService provisioningService,
            PlatformProperties properties,
            StringRedisTemplate redis,
            ApplicationContext applicationContext) {
        this.provisioningService = provisioningService;
        this.properties = properties;
        this.redis = redis;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        PlatformProperties.Fleet fleet = properties.getFleet();
        if (fleet == null || !fleet.isAutoMigrateOnStartup()) {
            log.info("Fleet auto-migrate on startup disabled (platform.fleet.auto-migrate-on-startup=false)");
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
            return;
        }

        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
        String lockOwner = UUID.randomUUID().toString();
        Duration lockTtl = Duration.ofSeconds(Math.max(60, fleet.getLockTtlSeconds()));
        boolean leader = Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(RedisKeys.FLEET_SCHEMA_MIGRATE_LOCK, lockOwner, lockTtl));

        try {
            if (leader) {
                runAsLeader(lockOwner, lockTtl);
            } else {
                waitForLeader(fleet.getFollowerWaitSeconds());
            }
        } finally {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    private void runAsLeader(String lockOwner, Duration lockTtl) {
        log.info("Fleet schema migrate: this replica is leader (lock={})", RedisKeys.FLEET_SCHEMA_MIGRATE_LOCK);
        try {
            Map<String, Object> result = provisioningService.migrateAllTenants();
            redis.opsForValue().set(
                    RedisKeys.FLEET_SCHEMA_MIGRATE_DONE,
                    String.valueOf(System.currentTimeMillis()),
                    lockTtl);
            log.info("Fleet schema migrate finished: tenants={} ok={} failed={}",
                    result.get("tenants"), result.get("ok"), result.get("failed"));
        } catch (Exception e) {
            log.error("Fleet schema migrate aborted: {}", e.getMessage(), e);
        } finally {
            releaseLock(lockOwner);
        }
    }

    private void waitForLeader(long followerWaitSeconds) {
        long deadline = System.currentTimeMillis() + Math.max(30, followerWaitSeconds) * 1000L;
        log.info("Fleet schema migrate: follower waiting for leader done signal (up to {}s)",
                followerWaitSeconds);
        String baseline = redis.opsForValue().get(RedisKeys.FLEET_SCHEMA_MIGRATE_DONE);
        while (System.currentTimeMillis() < deadline) {
            String done = redis.opsForValue().get(RedisKeys.FLEET_SCHEMA_MIGRATE_DONE);
            if (done != null && (baseline == null || !done.equals(baseline))) {
                log.info("Fleet schema migrate: leader finished (done={})", done);
                return;
            }
            // Leader may have finished before we read baseline — lock gone + done present
            Boolean lockHeld = redis.hasKey(RedisKeys.FLEET_SCHEMA_MIGRATE_LOCK);
            if (Boolean.FALSE.equals(lockHeld) && done != null) {
                log.info("Fleet schema migrate: lock released and done present — continuing");
                return;
            }
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Fleet schema migrate: follower wait interrupted");
                return;
            }
        }
        log.warn("Fleet schema migrate: follower wait timed out — accepting traffic (leader may still be migrating)");
    }

    private void releaseLock(String lockOwner) {
        try {
            String current = redis.opsForValue().get(RedisKeys.FLEET_SCHEMA_MIGRATE_LOCK);
            if (lockOwner.equals(current)) {
                redis.delete(RedisKeys.FLEET_SCHEMA_MIGRATE_LOCK);
            }
        } catch (Exception e) {
            log.warn("Fleet migrate lock release failed: {}", e.getMessage());
        }
    }
}
