package com.platform.hrms.extension.strategy;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantFieldDefinition;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * High-frequency filter via indexed {@code ext_indexed_attribute} (or configured table).
 * Prototype uses the generic extension table with attr_key / attr_value.
 */
@Component
public class ExtensionTableFilterStrategy implements FieldFilterStrategy {

    @Override
    public String storageType() {
        return "EXTENSION_TABLE";
    }

    @Override
    public Specification<Employee> toSpecification(TenantFieldDefinition definition, String filterValue) {
        String attrKey = definition.getFieldName();
        return (root, query, cb) -> {
            // EXISTS (select 1 from ext_indexed_attribute e where e.employee_id = root.id and e.attr_key=? and e.attr_value=?)
            Subquery<Integer> sq = query.subquery(Integer.class);
            var ext = sq.from(com.platform.hrms.domain.ExtIndexedAttribute.class);
            sq.select(cb.literal(1));
            sq.where(
                    cb.equal(ext.get("employeeId"), root.get("id")),
                    cb.equal(ext.get("attrKey"), attrKey),
                    cb.equal(ext.get("attrValue"), filterValue)
            );
            return cb.exists(sq);
        };
    }
}
