package com.platform.hrms.infrastructure.jobs;

import com.platform.common.tenant.TenantContext;
import com.platform.hrms.config.HrmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs a callback once per ACTIVE tenant with {@link TenantContext} bound.
 * Reusable for all multi-tenant scheduled jobs in HRMS.
 */
@Component
public class TenantJobRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantJobRunner.class);

    private final RestClient registryClient;
    private final String adminKey;
    private final String serviceName;

    public TenantJobRunner(
            HrmsProperties properties,
            RestClient.Builder restClientBuilder,
            @Value("${platform.security.admin-key:${PLATFORM_ADMIN_KEY:local-platform-admin-change-me}}") String adminKey) {
        this.registryClient = restClientBuilder.baseUrl(properties.getRegistry().getBaseUrl()).build();
        this.adminKey = adminKey;
        this.serviceName = properties.getServiceName() == null ? "hrms" : properties.getServiceName();
    }

    public void runForEachActiveTenant(String jobName, Consumer<String> tenantWork) {
        List<String> tenants = listActiveTenants();
        if (tenants.isEmpty()) {
            log.debug("{}: no active tenants", jobName);
            return;
        }
        int ok = 0;
        int failed = 0;
        for (String tenantId : tenants) {
            try {
                TenantContext.setTenantId(tenantId);
                tenantWork.accept(tenantId);
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("{} failed for tenant {}: {}", jobName, tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("{}: tenants={} ok={} failed={}", jobName, tenants.size(), ok, failed);
    }

    @SuppressWarnings("unchecked")
    private List<String> listActiveTenants() {
        try {
            List<Map<String, Object>> rows = registryClient.get()
                    .uri("/api/registry/tenants")
                    .header("X-Platform-Admin-Key", adminKey)
                    .retrieve()
                    .body(List.class);
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> m)) continue;
                Object svc = m.get("serviceName");
                Object tid = m.get("tenantId");
                Object status = m.get("status");
                if (svc == null || tid == null) continue;
                if (!serviceName.equalsIgnoreCase(String.valueOf(svc))) continue;
                if (status != null && !"ACTIVE".equalsIgnoreCase(String.valueOf(status))) continue;
                String id = String.valueOf(tid).trim();
                if (!id.isEmpty() && !out.contains(id)) {
                    out.add(id);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("TenantJobRunner: could not list tenants from registry: {}", e.getMessage());
            return List.of();
        }
    }
}
