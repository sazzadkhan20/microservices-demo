package com.platform.registry.api.dto;

import java.util.List;

public record ProvisionResponse(
        String tenantId,
        List<TenantRoutingView> routings,
        String message
) {
}
