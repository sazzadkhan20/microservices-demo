package com.platform.common.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantPoolLifecycleTest {

    @Test
    void resolve_reusesLivePool_andDoesNotCloseOnSecondLookup() throws Exception {
        TenantPoolLifecycle life = new TenantPoolLifecycle();
        AtomicInteger creates = new AtomicInteger();

        HikariDataSource first = life.resolve("t1", id -> {
            creates.incrementAndGet();
            return newH2(id);
        });
        try (Connection c = first.getConnection()) {
            assertFalse(c.isClosed());
            HikariDataSource second = life.resolve("t1", id -> {
                creates.incrementAndGet();
                return newH2(id);
            });
            assertSame(first, second);
            assertFalse(first.isClosed());
            assertFalse(c.isClosed());
        }
        assertTrue(creates.get() == 1);
        life.invalidate("t1");
        assertTrue(first.isClosed());
    }

    @Test
    void invalidate_closesOnlyThatTenant() {
        TenantPoolLifecycle life = new TenantPoolLifecycle();
        HikariDataSource a = life.resolve("a", id -> newH2(id));
        HikariDataSource b = life.resolve("b", id -> newH2(id));
        life.invalidate("a");
        assertTrue(a.isClosed());
        assertFalse(b.isClosed());
        life.invalidateAll();
        assertTrue(b.isClosed());
    }

    private static HikariDataSource newH2(String id) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("test-" + id);
        cfg.setJdbcUrl("jdbc:h2:mem:" + id + ";DB_CLOSE_DELAY=-1");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(0);
        return new HikariDataSource(cfg);
    }
}
