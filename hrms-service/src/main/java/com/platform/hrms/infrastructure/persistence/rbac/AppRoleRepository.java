package com.platform.hrms.infrastructure.persistence.rbac;

import com.platform.hrms.domain.rbac.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    Optional<AppRole> findByCode(String code);
}
