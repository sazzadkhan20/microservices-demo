package com.platform.registry.api.dto;

import com.platform.registry.domain.TenantRouting;

/**
 * Internal L3 payload for domain services (includes DB credentials).
 * Not meant for browsers / public Postman demos — call from HRMS/Payroll only.
 */
public record InternalRoutingResponse(
        String serviceName,
        String tenantId,
        String jdbcUrl,
        String username,
        String password,
        String status,
        long version
) {
    public static InternalRoutingResponse from(TenantRouting r) {
        return new InternalRoutingResponse(
                r.getServiceName(),
                r.getTenantId(),
                r.getJdbcUrl(),
                r.getUsername(),
                r.getPassword(),
                r.getStatus(),
                r.getVersion()
        );
    }
}
