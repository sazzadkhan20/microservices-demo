package com.platform.leave.infrastructure.persistence;

import com.platform.leave.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
}
