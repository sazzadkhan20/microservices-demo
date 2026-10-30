package com.platform.common.tenant;

import com.zaxxer.hikari.HikariDataSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Owns per-tenant Hikari pool lifecycle for database-per-tenant services.
 *
 * <p><b>Why this exists:</b> Caffeine L1 must remain a <em>lookup cache only</em>.
 * Closing pools from a Caffeine {@code removalListener} races with in-flight requests:
 * replacing a closed L1 entry calls {@code allPools.remove(key)} and shuts down the
 * <em>new</em> pool that was just registered — producing
 * {@code SQLSTATE 08003 This connection has been closed} on the first page load.
 *
 * <p>Reusable across HRMS / payroll / file / notification / report.
 * Open pools are capped ({@code maxOpenPools}); least-recently-used idle pools are closed first.
 */
public final class TenantPoolLifecycle {

    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    /** Soft lookup map — never closes pools on eviction. */
    private final ConcurrentHashMap<String, HikariDataSource> l1 = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAccessMs = new ConcurrentHashMap<>();
    private final int maxOpenPools;

    public TenantPoolLifecycle() {
        this(50);
    }

    public TenantPoolLifecycle(int maxOpenPools) {
        this.maxOpenPools = Math.max(1, maxOpenPools);
    }

    public HikariDataSource resolve(String tenantId, Function<String, HikariDataSource> factory) {
        HikariDataSource cached = l1.get(tenantId);
        if (cached != null && !cached.isClosed()) {
            touch(tenantId);
            return cached;
        }
        Object lock = locks.computeIfAbsent(tenantId, k -> new Object());
        synchronized (lock) {
            cached = l1.get(tenantId);
            if (cached != null && !cached.isClosed()) {
                touch(tenantId);
                return cached;
            }
            if (cached != null) {
                l1.remove(tenantId, cached);
            }
            HikariDataSource existing = pools.get(tenantId);
            if (existing != null && !existing.isClosed()) {
                l1.put(tenantId, existing);
                touch(tenantId);
                return existing;
            }
            if (existing != null) {
                pools.remove(tenantId, existing);
                closeQuietly(existing);
            }
            evictIfNeeded(tenantId);
            HikariDataSource created = factory.apply(tenantId);
            pools.put(tenantId, created);
            l1.put(tenantId, created);
            touch(tenantId);
            return created;
        }
    }

    private void touch(String tenantId) {
        lastAccessMs.put(tenantId, System.currentTimeMillis());
    }

    /** Close least-recently-used pools until under the cap (never evict the tenant being resolved). */
    private void evictIfNeeded(String keepTenantId) {
        while (pools.size() >= maxOpenPools) {
            String victim = pools.keySet().stream()
                    .filter(id -> !id.equals(keepTenantId))
                    .min(Comparator.comparingLong(id -> lastAccessMs.getOrDefault(id, 0L)))
                    .orElse(null);
            if (victim == null) {
                break;
            }
            l1.remove(victim);
            closeQuietly(pools.remove(victim));
            locks.remove(victim);
            lastAccessMs.remove(victim);
        }
    }

    public void invalidate(String tenantId) {
        l1.remove(tenantId);
        closeQuietly(pools.remove(tenantId));
        locks.remove(tenantId);
        lastAccessMs.remove(tenantId);
    }

    public void invalidateAll() {
        l1.clear();
        for (String id : new ArrayList<>(pools.keySet())) {
            closeQuietly(pools.remove(id));
        }
        locks.clear();
        lastAccessMs.clear();
    }

    public Map<String, HikariDataSource> snapshot() {
        return Map.copyOf(pools);
    }

    public static void closeQuietly(HikariDataSource ds) {
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }
}
