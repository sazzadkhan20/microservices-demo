package com.platform.hrms.extension.strategy;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantFieldDefinition;
import org.springframework.data.jpa.domain.Specification;

/**
 * Strategy (OCP): add new storage types without changing the query engine.
 */
public interface FieldFilterStrategy {

    /** Storage type this strategy handles, e.g. JSONB / EXTENSION_TABLE */
    String storageType();

    Specification<Employee> toSpecification(TenantFieldDefinition definition, String filterValue);
}
