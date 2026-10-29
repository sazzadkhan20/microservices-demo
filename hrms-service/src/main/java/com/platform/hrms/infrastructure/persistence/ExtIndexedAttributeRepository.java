package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.ExtIndexedAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtIndexedAttributeRepository extends JpaRepository<ExtIndexedAttribute, ExtIndexedAttribute.Pk> {
}
