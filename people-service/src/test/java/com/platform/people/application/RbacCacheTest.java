package com.platform.people.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacCacheTest {

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.unbindResourceIfPossible(RbacCache.class.getName() + ".txDirty");
    }

    @Test
    void cachesUntilInvalidatedByAnRbacChange() {
        RbacCache cache = new RbacCache(60);
        AtomicInteger loads = new AtomicInteger();
        assertEquals("v1", cache.get("k", k -> "v" + loads.incrementAndGet()));
        assertEquals("v1", cache.get("k", k -> "v" + loads.incrementAndGet()));
        assertEquals(1, loads.get(), "second read served from cache");

        RbacCache.invalidateAfterCommit(); // what the JPA listener calls on any RBAC/employee change
        assertEquals("v2", cache.get("k", k -> "v" + loads.incrementAndGet()));
    }

    @Test
    void changeInsideTransactionMakesRestOfItReadFreshAndInvalidatesOnCompletion() {
        RbacCache cache = new RbacCache(60);
        TransactionSynchronizationManager.initSynchronization();
        assertFalse(RbacCache.transactionDirty(), "ordinary transactions use the cache");

        RbacCache.invalidateAfterCommit();
        assertTrue(RbacCache.transactionDirty(), "after an RBAC change the tx reads current state");

        cache.get("k", k -> "stale-reader");
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        assertFalse(RbacCache.transactionDirty(), "flag is scoped to the transaction");
        assertEquals("fresh", cache.get("k", k -> "fresh"), "entries cached during the tx are dropped after commit");
    }
}
