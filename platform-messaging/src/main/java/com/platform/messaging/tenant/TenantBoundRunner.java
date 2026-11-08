package com.platform.messaging.tenant;

import com.platform.common.tenant.TenantContext;

import java.util.function.Supplier;

/** Binds tenant ThreadLocal for async consumers / outbox relay. */
public final class TenantBoundRunner {

    private TenantBoundRunner() {
    }

    public static void run(String tenantId, Runnable action) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId.trim());
            action.run();
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previous);
            }
        }
    }

    public static <T> T call(String tenantId, Supplier<T> action) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId.trim());
            return action.get();
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previous);
            }
        }
    }
}
