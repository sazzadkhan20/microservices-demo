package com.platform.people.infrastructure.persistence;

import com.platform.people.domain.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    Optional<Designation> findByCode(String code);

    List<Designation> findByApproverRole(String approverRole);
}
