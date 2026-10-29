package com.platform.hrms.api.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.hrms.api.grpc.EmployeeResponse;
import com.platform.hrms.api.grpc.EmployeeServiceGrpc;
import com.platform.hrms.api.grpc.GetEmployeeRequest;
import com.platform.hrms.api.grpc.GetPermissionsRequest;
import com.platform.hrms.api.grpc.PermissionsResponse;
import com.platform.hrms.application.EmployeeService;
import com.platform.hrms.application.RbacService;
import com.platform.hrms.domain.Employee;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Internal gRPC API for Payroll (and future services).
 * Never share the HRMS database — call this instead.
 */
@GrpcService
public class EmployeeGrpcService extends EmployeeServiceGrpc.EmployeeServiceImplBase {

    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;
    private final RbacService rbacService;

    public EmployeeGrpcService(EmployeeService employeeService, ObjectMapper objectMapper, RbacService rbacService) {
        this.employeeService = employeeService;
        this.objectMapper = objectMapper;
        this.rbacService = rbacService;
    }

    @Override
    public void getPermissions(GetPermissionsRequest request, StreamObserver<PermissionsResponse> responseObserver) {
        try {
            PermissionsResponse response = PermissionsResponse.newBuilder()
                    .addAllPermissionKeys(rbacService.permissionKeysFor(request.getEmployeeId()))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getEmployee(GetEmployeeRequest request, StreamObserver<EmployeeResponse> responseObserver) {
        try {
            Employee employee = employeeService.requireEntity(request.getEmployeeId());
            String customJson = objectMapper.writeValueAsString(employee.getCustomFields());
            EmployeeResponse response = EmployeeResponse.newBuilder()
                    .setId(employee.getId())
                    .setEmployeeCode(employee.getEmployeeCode())
                    .setFullName(employee.getFullName())
                    .setEmail(employee.getEmail())
                    .setBaseSalary(employee.getBaseSalary().toPlainString())
                    .setHourlyRate(employee.getHourlyRate().toPlainString())
                    .setCustomFieldsJson(customJson)
                    .setActive(employee.isActive())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }
}
