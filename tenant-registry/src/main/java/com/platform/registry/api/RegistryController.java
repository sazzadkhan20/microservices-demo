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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registry")
@Tag(name = "Tenant Registry", description = "Provision tenants, inspect routing, issue JWTs")
public class RegistryController {

    private static final long TOKEN_TTL = 3600;

    private final ProvisioningService provisioningService;
    private final TenantRoutingRepository routingRepository;
    private final JwtSupport jwtSupport;
    private final PlatformAuthSupport platformAuth;

    public RegistryController(
            ProvisioningService provisioningService,
            TenantRoutingRepository routingRepository,
            JwtSupport jwtSupport,
            PlatformAuthSupport platformAuth) {
        this.provisioningService = provisioningService;
        this.routingRepository = routingRepository;
        this.jwtSupport = jwtSupport;
        this.platformAuth = platformAuth;
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

    @GetMapping("/routing/{service}/{tenantId}")
    @Operation(summary = "Public routing view (no credentials)")
    public TenantRoutingView routing(@PathVariable String service, @PathVariable String tenantId) {
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
    @Operation(summary = "Account-picker JWT — tenant must already be provisioned; employeeId required for product sessions")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        String tenantId = TenantIds.normalize(request.tenantId());
        routingRepository.findByServiceNameAndTenantId("hrms", tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or unprovisioned tenant: " + tenantId));
        if (request.employeeId() == null) {
            throw new IllegalArgumentException("employeeId is required for account-picker sessions");
        }
        String subject = (request.subject() == null || request.subject().isBlank())
                ? "employee-" + request.employeeId()
                : request.subject();
        String jwt = jwtSupport.issueToken(subject, tenantId, request.employeeId(), TOKEN_TTL);
        return TokenResponse.of(jwt, tenantId, request.employeeId(), TOKEN_TTL);
    }

    @GetMapping("/health-info")
    @Operation(summary = "Simple registry info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "tenant-registry",
                "role", "control-plane",
                "auth", Map.of(
                        "adminHeader", PlatformAuthSupport.ADMIN_HEADER,
                        "serviceHeader", PlatformAuthSupport.SERVICE_HEADER
                )
        );
    }
}
