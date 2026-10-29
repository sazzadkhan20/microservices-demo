package com.platform.registry.api.dto;

import com.platform.registry.domain.TenantRouting;

public record TenantRoutingView(
        String serviceName,
        String tenantId,
        String jdbcUrl,
        String status,
        long version
) {
    public static TenantRoutingView from(TenantRouting r) {
        return new TenantRoutingView(
                r.getServiceName(),
                r.getTenantId(),
                r.getJdbcUrl(),
                r.getStatus(),
                r.getVersion()
        );
    }
}
