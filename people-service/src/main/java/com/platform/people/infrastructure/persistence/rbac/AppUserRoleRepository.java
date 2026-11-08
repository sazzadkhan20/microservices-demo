package com.platform.people.infrastructure.persistence.rbac;

import com.platform.people.domain.rbac.AppUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppUserRoleRepository extends JpaRepository<AppUserRole, Long> {
    List<AppUserRole> findByEmployeeId(Long employeeId);
    List<AppUserRole> findByRoleId(Long roleId);
    void deleteByEmployeeId(Long employeeId);
    boolean existsByRoleId(Long roleId);
}
