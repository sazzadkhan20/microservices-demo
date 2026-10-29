package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
}
