package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.TenantFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantFieldDefinitionRepository extends JpaRepository<TenantFieldDefinition, Long> {
    Optional<TenantFieldDefinition> findByFieldName(String fieldName);
}
