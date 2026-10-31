package com.platform.registry.api;

import com.platform.common.security.JwtSupport;
import com.platform.common.tenant.TenantIds;
import com.platform.registry.api.dto.InternalRoutingResponse;
import com.platform.registry.api.dto.ProvisionRequest;
import com.platform.registry.api.dto.ProvisionResponse;
import com.platform.registry.api.dto.TenantRoutingView;
import com.platform.registry.api.dto.TokenRequest;
import com.platform.registry.api.dto.TokenResponse;
import com.platform.registry.application.ProvisioningService;
import com.platform.registry.config.PlatformProperties;
import com.platform.registry.domain.TenantRoutingStatuses;
import com.platform.registry.infrastructure.persistence.TenantRoutingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registry")
@Tag(name = "Tenant Registry", description = "Provision tenants, inspect routing, issue JWTs")
public class RegistryController {

    private final ProvisioningService provisioningService;
    private final TenantRoutingRepository routingRepository;
    private final JwtSupport jwtSupport;
    private final PlatformAuthSupport platformAuth;
    private final PlatformProperties platformProperties;
    private final long tokenTtlSeconds;
    private final org.springframework.web.client.RestClient hrmsClient;

    public RegistryController(
            ProvisioningService provisioningService,
            TenantRoutingRepository routingRepository,
            JwtSupport jwtSupport,
            PlatformAuthSupport platformAuth,
            PlatformProperties platformProperties,
            @org.springframework.beans.factory.annotation.Value("${platform.security.token-ttl-seconds:604800}") long tokenTtlSeconds,
            @org.springframework.beans.factory.annotation.Value("${HRMS_BASE_URL:http://127.0.0.1:8081}") String hrmsBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey,
            @org.springframework.beans.factory.annotation.Value("${platform.security.internal-key:local-internal-key-change-me}") String internalKey,
            org.springframework.web.client.RestClient.Builder restClientBuilder) {
        this.provisioningService = provisioningService;
        this.routingRepository = routingRepository;
        this.jwtSupport = jwtSupport;
        this.platformAuth = platformAuth;
        this.platformProperties = platformProperties;
        this.tokenTtlSeconds = tokenTtlSeconds > 0 ? tokenTtlSeconds : 604800L;
        this.hrmsClient = restClientBuilder
                .baseUrl(hrmsBaseUrl == null ? "http://127.0.0.1:8081" : hrmsBaseUrl.replaceAll("/$", ""))
                .defaultHeader(com.platform.common.constants.PlatformHeaders.SERVICE_KEY, serviceKey)
                .defaultHeader(com.platform.common.security.InternalCalls.HEADER, internalKey)
                .build();
    }

    @PostMapping("/provision")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Provision a new tenant (requires X-Platform-Admin-Key)")
    public ProvisionResponse provision(
            @RequestHeader(value = PlatformAuthSupport.ADMIN_HEADER, required = false) String adminKey,
            @Valid @RequestBody ProvisionRequest request) {
        platformAuth.requireAdmin(adminKey);
        return provisioningService.provision(request);
    }

    @GetMapping("/tenants")
    @Operation(summary = "List tenant routing (requires X-Platform-Admin-Key; JDBC URLs redacted for safety)")
    public List<TenantRoutingView> listTenants(
            @RequestHeader(value = PlatformAuthSupport.ADMIN_HEADER, required = false) String adminKey) {
        platformAuth.requireAdmin(adminKey);
        return provisioningService.listAll();
    }

    @PostMapping("/tenants/{tenantId}/upgrade-hrms")
    @Operation(summary = "Migrate/reseed HRMS (requires X-Platform-Admin-Key)")
    public Map<String, Object> upgradeHrms(
            @RequestHeader(value = PlatformAuthSupport.ADMIN_HEADER, required = false) String adminKey,
            @PathVariable String tenantId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean reseed,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String businessPack) {
        platformAuth.requireAdmin(adminKey);
        return provisioningService.upgradeHrmsSchema(tenantId, reseed, businessPack);
    }

    @PostMapping("/tenants/{tenantId}/upgrade-payroll")
    @Operation(summary = "Migrate payroll tenant schema (requires X-Platform-Admin-Key)")
    public Map<String, Object> upgradePayroll(
            @RequestHeader(value = PlatformAuthSupport.ADMIN_HEADER, required = false) String adminKey,
            @PathVariable String tenantId) {
        platformAuth.requireAdmin(adminKey);
        return provisioningService.upgradePayrollSchema(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/upgrade-rbac")
    @Operation(summary = "Re-apply system role permission matrix without wiping data (X-Platform-Admin-Key)")
    public Map<String, Object> upgradeRbac(
            @RequestHeader(value = PlatformAuthSupport.ADMIN_HEADER, required = false) String adminKey,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "qii_creative") String businessPack) {
        platformAuth.requireAdmin(adminKey);
        return provisioningService.upgradeRbacMatrix(tenantId, businessPack);
    }

    @PostMapping("/tenants/{tenantId}/upgrade-satellites")
    @Operation(summary = "Migrate file/notification/report to database-per-tenant (requires X-Platform-Admin-Key)")
    public Map<String, Object> upgradeSatellites(
            @RequestHeader(value = PlatformAuthSupport.ADMIN_HEADER, required = false) String adminKey,
            @PathVariable String tenantId) {
        platformAuth.requireAdmin(adminKey);
        return provisioningService.upgradeSatelliteSchemas(tenantId);
    }

    @PostMapping("/tenants/migrate-all")
    @Operation(summary = "Fleet-migrate tenant DBs to the shared product schema "
            + "(no reseed; failed rows → SCHEMA_OUTDATED; requires X-Platform-Admin-Key). "
            + "Optional ?service=hrms|payroll|file|notification|report migrates only that service's "
            + "tenant DBs. Omitting service migrates all. Also runs automatically on registry startup.")
    public Map<String, Object> migrateAll(
            @RequestHeader(value = PlatformAuthSupport.ADMIN_HEADER, required = false) String adminKey,
            @RequestParam(required = false) String service) {
        platformAuth.requireAdmin(adminKey);
        return provisioningService.migrateAllTenants(service);
    }

    @GetMapping("/routing/{service}/{tenantId}")
    @Operation(summary = "Routing view without credentials (requires X-Service-Key)")
    public TenantRoutingView routing(
            @RequestHeader(value = PlatformAuthSupport.SERVICE_HEADER, required = false) String serviceKey,
            @PathVariable String service,
            @PathVariable String tenantId) {
        platformAuth.requireService(serviceKey);
        return provisioningService.getRouting(service, tenantId);
    }

    @GetMapping("/internal/routing/{service}/{tenantId}")
    @Operation(summary = "L3 routing with DB credentials — service-to-service only (X-Service-Key)")
    public InternalRoutingResponse internalRouting(
            @RequestHeader(value = PlatformAuthSupport.SERVICE_HEADER, required = false) String serviceKey,
            @PathVariable String service,
            @PathVariable String tenantId) {
        platformAuth.requireService(serviceKey);
        String normalized = TenantIds.normalize(tenantId);
        return routingRepository.findByServiceNameAndTenantId(service, normalized)
                .map(InternalRoutingResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No routing for service=" + service + " tenant=" + normalized));
    }

    @PostMapping("/token")
    @Operation(summary = "Account-picker JWT — requires X-Service-Key (gateway adds it after X-Demo-Auth-Key check); "
            + "tenant must already be provisioned; employee must exist and be active in HRMS")
    public TokenResponse token(
            @RequestHeader(value = PlatformAuthSupport.SERVICE_HEADER, required = false) String svcKey,
            @Valid @RequestBody TokenRequest request) {
        platformAuth.requireService(svcKey);
        String tenantId = TenantIds.normalize(request.tenantId());
        // Require full core routing ACTIVE (reject half-provisioned or SCHEMA_OUTDATED tenants)
        for (String svc : java.util.List.of("hrms", "payroll")) {
            var row = routingRepository.findByServiceNameAndTenantId(svc, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown or incompletely provisioned tenant: " + tenantId + " (missing " + svc + ")"));
            if (!TenantRoutingStatuses.isActive(row.getStatus())) {
                throw new IllegalStateException(
                        "Tenant " + tenantId + " service " + svc + " is not ready (status="
                                + row.getStatus() + "). Fleet schema migrate required.");
            }
        }
        if (request.employeeId() == null) {
            throw new IllegalArgumentException("employeeId is required for account-picker sessions");
        }
        requireActiveEmployee(tenantId, request.employeeId());
        String subject = (request.subject() == null || request.subject().isBlank())
                ? "employee-" + request.employeeId()
                : request.subject();
        String jwt = jwtSupport.issueToken(subject, tenantId, request.employeeId(), tokenTtlSeconds);
        return TokenResponse.of(jwt, tenantId, request.employeeId(), tokenTtlSeconds);
    }

    private void requireActiveEmployee(String tenantId, Long employeeId) {
        try {
            Map<?, ?> body = hrmsClient.get()
                    .uri("/api/internal/employees/{id}/auth-check", employeeId)
                    .header(com.platform.common.constants.PlatformHeaders.TENANT_ID, tenantId)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !Boolean.TRUE.equals(body.get("active"))) {
                throw new IllegalArgumentException("Employee not found or inactive: " + employeeId);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 404 || code == 400) {
                throw new IllegalArgumentException("Employee not found or inactive: " + employeeId);
            }
            throw new IllegalArgumentException("Employee not found or inactive: " + employeeId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Employee not found or inactive: " + employeeId);
        }
    }

    @GetMapping("/health-info")
    @Operation(summary = "Simple registry info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "tenant-registry",
                "role", "control-plane",
                "fleet", Map.of(
                        "autoMigrateOnStartup", platformProperties.getFleet().isAutoMigrateOnStartup()
                ),
                "auth", Map.of(
                        "adminHeader", PlatformAuthSupport.ADMIN_HEADER,
                        "serviceHeader", PlatformAuthSupport.SERVICE_HEADER
                )
        );
    }
}
