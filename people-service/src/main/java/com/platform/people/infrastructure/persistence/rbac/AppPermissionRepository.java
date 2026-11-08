package com.platform.people.infrastructure.persistence.rbac;

import com.platform.people.domain.rbac.AppPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppPermissionRepository extends JpaRepository<AppPermission, Long> {
    Optional<AppPermission> findByFeatureAndAction(String feature, String action);
}
