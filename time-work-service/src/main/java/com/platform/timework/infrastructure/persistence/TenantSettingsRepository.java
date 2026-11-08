package com.platform.timework.infrastructure.persistence;

import com.platform.timework.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
}
