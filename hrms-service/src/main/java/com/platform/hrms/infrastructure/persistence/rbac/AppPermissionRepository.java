package com.platform.hrms.infrastructure.persistence.rbac;

import com.platform.hrms.domain.rbac.AppPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppPermissionRepository extends JpaRepository<AppPermission, Long> {
    Optional<AppPermission> findByFeatureAndAction(String feature, String action);
}
