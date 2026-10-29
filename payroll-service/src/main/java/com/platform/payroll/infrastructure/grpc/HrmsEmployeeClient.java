package com.platform.payroll.infrastructure.grpc;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import com.platform.hrms.api.grpc.EmployeeResponse;
import com.platform.hrms.api.grpc.EmployeeServiceGrpc;
import com.platform.hrms.api.grpc.GetEmployeeRequest;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * gRPC client to HRMS. Propagates current tenant via metadata.
 */
@Component
public class HrmsEmployeeClient {

    private static final Metadata.Key<String> TENANT_KEY =
            Metadata.Key.of(PlatformHeaders.GRPC_TENANT_ID, Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("hrmsEmployee")
    private EmployeeServiceGrpc.EmployeeServiceBlockingStub stub;

    public EmployeeSnapshot getEmployee(long employeeId) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext empty — cannot call HRMS");
        }
        Metadata headers = new Metadata();
        headers.put(TENANT_KEY, tenantId);

        EmployeeResponse response = stub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
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
