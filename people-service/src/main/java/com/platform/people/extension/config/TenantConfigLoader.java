package com.platform.people.extension.config;

import com.platform.people.domain.TenantFieldDefinition;
import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.TenantFieldDefinitionRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * People-owned tenant config (settings + field definitions). Leave policy lives in leave-service.
 */
@Component
public class TenantConfigLoader {

    private final TenantFieldDefinitionRepository fieldDefinitionRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public TenantConfigLoader(
            TenantFieldDefinitionRepository fieldDefinitionRepository,
            TenantSettingsRepository tenantSettingsRepository) {
        this.fieldDefinitionRepository = fieldDefinitionRepository;
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

    @Transactional(readOnly = true)
    public List<TenantFieldDefinition> loadFieldDefinitions() {
        return fieldDefinitionRepository.findAll();
    }
}
