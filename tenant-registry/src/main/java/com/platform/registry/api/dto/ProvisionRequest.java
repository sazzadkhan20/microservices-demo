package com.platform.registry.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Provision a tenant. Use {@code businessPack} to seed a known practice config:
 * {@code techshop_retail} or {@code impit_limited}.
 * Legacy {@code seedPracticeBusiness=true} maps to techshop_retail.
 */
public record ProvisionRequest(
        @NotBlank(message = "tenantId is required") String tenantId,
        String displayName,
        Boolean seedPracticeBusiness,
        /** techshop_retail | impit_limited | null */
        String businessPack
) {
    public String resolvedBusinessPack() {
        if (businessPack != null && !businessPack.isBlank()) {
            return businessPack.trim().toLowerCase();
        }
        if (Boolean.TRUE.equals(seedPracticeBusiness)) {
            return "techshop_retail";
        }
        return null;
    }
}
