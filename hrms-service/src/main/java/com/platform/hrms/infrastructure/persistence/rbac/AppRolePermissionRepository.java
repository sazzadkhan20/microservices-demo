package com.platform.hrms.infrastructure.persistence.rbac;

import com.platform.hrms.domain.rbac.AppRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppRolePermissionRepository extends JpaRepository<AppRolePermission, Long> {
    List<AppRolePermission> findByRoleIdIn(Iterable<Long> roleIds);
    void deleteByRoleId(Long roleId);
}
