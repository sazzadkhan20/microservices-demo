package com.platform.people.infrastructure.persistence.rbac;

import com.platform.people.domain.rbac.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    Optional<AppRole> findByCode(String code);
}
