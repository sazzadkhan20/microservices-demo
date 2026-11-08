package com.platform.people.infrastructure.persistence;

import com.platform.people.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
}
