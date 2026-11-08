package com.platform.timework.extension.config;

import com.platform.timework.domain.TenantSettings;
import com.platform.timework.infrastructure.persistence.TenantSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Time&amp;Work-owned settings ({@code timework_settings}: schedule/attendance/worklog/task rules).
 */
@Component
public class TenantConfigLoader {

    private final TenantSettingsRepository tenantSettingsRepository;

    public TenantConfigLoader(TenantSettingsRepository tenantSettingsRepository) {
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadSettingsMap() {
        return tenantSettingsRepository.findAll().stream()
                .findFirst()
                .map(TenantSettings::getSettings)
                .map(s -> s == null ? Map.<String, Object>of() : new HashMap<>(s))
                .orElseGet(Map::of);
    }
}
