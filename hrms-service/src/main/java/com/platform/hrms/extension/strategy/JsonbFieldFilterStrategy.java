package com.platform.hrms.extension.strategy;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantFieldDefinition;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Filters against {@code employee.custom_fields} JSONB using PostgreSQL {@code ->>} operator.
 */
@Component
public class JsonbFieldFilterStrategy implements FieldFilterStrategy {

    @Override
    public String storageType() {
        return "JSONB";
    }

    @Override
    public Specification<Employee> toSpecification(TenantFieldDefinition definition, String filterValue) {
        // jsonb_path like custom_fields.store_id → key = store_id
        String key = extractKey(definition);
        return (root, query, cb) -> {
            // function(custom_fields ->> key) = value
            var jsonExtract = cb.function(
                    "jsonb_extract_path_text",
                    String.class,
                    root.get("customFields"),
                    cb.literal(key)
            );
            return cb.equal(jsonExtract, filterValue);
        };
    }

    private String extractKey(TenantFieldDefinition definition) {
        String path = definition.getJsonbPath();
        if (path == null || path.isBlank()) {
            return definition.getFieldName();
        }
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
