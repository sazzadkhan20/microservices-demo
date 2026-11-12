package com.platform.people.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Function;

/**
 * Short-lived cache of per-employee RBAC reads (roles, permission keys, scopes).
 *
 * <p>Every request in People is permission-checked and every other service resolves authority
 * through People, so uncached RBAC was the first bottleneck under load (4–5 queries per request).
 * Entries are dropped after commit of any change to roles, role permissions, role assignments,
 * permissions or employees ({@link RbacCacheInvalidationListener}); the TTL only bounds staleness
 * across People replicas (guide §4: bounded cache delay for ordinary reads, documented in
 * docs/SYSTEM_HARDENING.md).
 */
@Component
public class RbacCache {

    private static volatile RbacCache instance;
    private static final Object TX_DIRTY_KEY = RbacCache.class.getName() + ".txDirty";

    private final Cache<Object, Object> cache;

    public RbacCache(@Value("${people.rbac.cache-ttl-seconds:10}") long ttlSeconds) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(0, ttlSeconds)))
                .build();
        instance = this;
    }

    @SuppressWarnings("unchecked")
    <K, V> V get(K key, Function<K, V> loader) {
        return (V) cache.get(key, k -> loader.apply((K) k));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Mark the current transaction as one that changes (or authorises changes to) RBAC data: every
     * RBAC read for the rest of it goes to the database, so privileged writes check current authority
     * and see their own uncommitted changes.
     */
    public static void markTransactionDirty() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(TX_DIRTY_KEY)) {
            return;
        }
        TransactionSynchronizationManager.bindResource(TX_DIRTY_KEY, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(TX_DIRTY_KEY);
            }
        });
    }

    public static boolean transactionDirty() {
        return TransactionSynchronizationManager.hasResource(TX_DIRTY_KEY);
    }

    /**
     * Invalidate now and again after the current transaction commits, so a concurrent reader
     * cannot repopulate the cache with pre-commit data.
     */
    static void invalidateAfterCommit() {
        markTransactionDirty();
        RbacCache c = instance;
        if (c == null) {
            return;
        }
        c.invalidateAll();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    c.invalidateAll();
                }
            });
        }
    }
}
