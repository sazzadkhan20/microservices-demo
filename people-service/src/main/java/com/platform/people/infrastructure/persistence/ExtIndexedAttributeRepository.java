package com.platform.people.infrastructure.persistence;

import com.platform.people.domain.ExtIndexedAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtIndexedAttributeRepository extends JpaRepository<ExtIndexedAttribute, ExtIndexedAttribute.Pk> {
}
