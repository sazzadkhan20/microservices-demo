package com.platform.hrms.infrastructure.persistence.rbac;

import com.platform.hrms.domain.rbac.AppUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppUserRoleRepository extends JpaRepository<AppUserRole, Long> {
    List<AppUserRole> findByEmployeeId(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
}
