package com.platform.payroll.infrastructure.grpc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import com.platform.hrms.api.grpc.EmployeeResponse;
import com.platform.hrms.api.grpc.EmployeeServiceGrpc;
import com.platform.hrms.api.grpc.GetEmployeeRequest;
import com.platform.hrms.api.grpc.GetPermissionsRequest;
import com.platform.hrms.api.grpc.PermissionsResponse;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * gRPC client to HRMS. Propagates current tenant via metadata and authenticates with the shared
 * service key. Also resolves RBAC permissions (HRMS owns roles/permissions; Payroll never reads that DB).
 */
@Component
public class HrmsEmployeeClient {

    private static final Metadata.Key<String> TENANT_KEY =
            Metadata.Key.of(PlatformHeaders.GRPC_TENANT_ID, Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SERVICE_KEY =
            Metadata.Key.of(PlatformHeaders.GRPC_SERVICE_KEY, Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("hrmsEmployee")
    private EmployeeServiceGrpc.EmployeeServiceBlockingStub stub;

    private final String serviceKey;

    /** Short TTL so role changes in HRMS take effect quickly while avoiding a gRPC call per request. */
    private final Cache<String, Set<String>> permissionCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .maximumSize(5_000)
            .build();

    public HrmsEmployeeClient(
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public EmployeeSnapshot getEmployee(long employeeId) {
        EmployeeResponse response = stubWithHeaders()
                .getEmployee(GetEmployeeRequest.newBuilder().setEmployeeId(employeeId).build());

        return new EmployeeSnapshot(
                response.getId(),
                response.getEmployeeCode(),
                response.getFullName(),
                new BigDecimal(response.getHourlyRate()),
                new BigDecimal(response.getBaseSalary()),
                response.getActive()
        );
    }

    /** Effective lower-case permission keys ("feature:action", "*:*") for the employee in the current tenant. */
    public Set<String> getPermissions(long employeeId) {
        String tenantId = requireTenant();
        return permissionCache.get(tenantId + ":" + employeeId, k -> {
            PermissionsResponse response = stubWithHeaders()
                    .getPermissions(GetPermissionsRequest.newBuilder().setEmployeeId(employeeId).build());
            return Set.copyOf(new HashSet<>(response.getPermissionKeysList()));
        });
    }

    private EmployeeServiceGrpc.EmployeeServiceBlockingStub stubWithHeaders() {
        Metadata headers = new Metadata();
        headers.put(TENANT_KEY, requireTenant());
        headers.put(SERVICE_KEY, serviceKey);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static String requireTenant() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext empty — cannot call HRMS");
        }
        return tenantId;
    }

    public record EmployeeSnapshot(
            long id,
            String employeeCode,
            String fullName,
            BigDecimal hourlyRate,
            BigDecimal baseSalary,
            boolean active
    ) {
    }
}
