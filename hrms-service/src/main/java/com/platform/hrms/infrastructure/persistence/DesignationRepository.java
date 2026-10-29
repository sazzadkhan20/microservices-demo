package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    Optional<Designation> findByCode(String code);

    List<Designation> findByApproverRole(String approverRole);
}
