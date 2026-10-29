package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.common.tenant.TenantContext;
import com.platform.common.tenant.TenantIds;
import com.platform.hrms.domain.Department;
import com.platform.hrms.domain.Designation;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.infrastructure.persistence.DepartmentRepository;
import com.platform.hrms.infrastructure.persistence.DesignationRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthFacadeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final RbacService rbacService;

    public AuthFacadeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            RbacService rbacService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.rbacService = rbacService;
    }

    /**
     * Public account picker — tenant must already be bound by TenantFilter (header or ?tenantId=).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAccounts(String tenantId) {
        String normalized = TenantIds.normalize(tenantId);
        if (TenantContext.getTenantId() == null || TenantContext.getTenantId().isBlank()) {
            TenantContext.setTenantId(normalized);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Employee e : employeeRepository.findAll()) {
            if (!e.isActive()) continue;
            if (e.getStatus() != null && List.of("Inactive", "Resigned", "Terminated").contains(e.getStatus())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", String.valueOf(e.getId()));
            row.put("name", e.getFullName());
            row.put("email", e.getEmail());
            row.put("tenantId", normalized);
            List<String> roles = rbacService.roleCodesFor(e.getId());
            row.put("role", roles.isEmpty() ? "employee" : String.join(", ", roles));
            departmentRepository.findById(e.getDepartmentId() == null ? -1L : e.getDepartmentId())
                    .map(Department::getName).ifPresent(n -> row.put("department", n));
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        Long employeeId = AuthContext.requireEmployeeId();
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        List<String> roles = rbacService.roleCodesFor(employeeId);
        Set<String> perms = rbacService.permissionKeysFor(employeeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", String.valueOf(e.getId()));
        m.put("name", e.getFullName());
        m.put("email", e.getEmail());
        m.put("tenantId", TenantContext.getTenantId());
        m.put("roles", roles);
        m.put("permissions", new ArrayList<>(perms));
        m.put("department", e.getDepartmentId() == null ? null :
                departmentRepository.findById(e.getDepartmentId()).map(Department::getName).orElse(null));
        m.put("position", e.getJobTitle() != null ? e.getJobTitle() :
                (e.getDesignationId() == null ? null :
                        designationRepository.findById(e.getDesignationId()).map(Designation::getName).orElse(null)));
        m.put("phoneNumber", e.getPhone());
        m.put("joinDate", e.getStartDate() == null ? null : e.getStartDate().toString());
        m.put("status", e.getStatus());
        return m;
    }
}
