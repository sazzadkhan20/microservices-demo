package com.platform.hrms.extension.engine;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantFieldDefinition;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.extension.strategy.FieldFilterStrategy;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config-driven query engine (no per-tenant Java classes).
 *
 * <p>Reads field definitions from the tenant DB and applies the matching strategy.
 * If no definitions exist yet (prototype), custom filters are ignored (platform default).
 */
@Component
public class EmployeeQueryEngine {

    private final TenantConfigLoader configLoader;
    private final Map<String, FieldFilterStrategy> strategiesByType = new HashMap<>();

    public EmployeeQueryEngine(TenantConfigLoader configLoader, List<FieldFilterStrategy> strategies) {
        this.configLoader = configLoader;
        for (FieldFilterStrategy strategy : strategies) {
            strategiesByType.put(strategy.storageType().toUpperCase(), strategy);
        }
    }

    public Specification<Employee> buildSpec(Map<String, String> filters) {
        // Start with "match all"; AND custom filter specs when field definitions exist
        Specification<Employee> spec = (root, query, cb) -> cb.conjunction();
        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        List<TenantFieldDefinition> definitions = configLoader.loadFieldDefinitions();
        Map<String, TenantFieldDefinition> byName = new HashMap<>();
        for (TenantFieldDefinition def : definitions) {
            byName.put(def.getFieldName(), def);
        }

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            TenantFieldDefinition def = byName.get(entry.getKey());
            if (def == null || !def.isFilterable()) {
                // Unknown custom filter in prototype → ignore (keeps API stable)
                continue;
            }
            FieldFilterStrategy strategy = strategiesByType.get(def.getStorageType().toUpperCase());
            if (strategy == null) {
                continue;
            }
            Specification<Employee> part = strategy.toSpecification(def, entry.getValue());
            spec = spec == null ? part : spec.and(part);
        }
        return spec;
    }
}
