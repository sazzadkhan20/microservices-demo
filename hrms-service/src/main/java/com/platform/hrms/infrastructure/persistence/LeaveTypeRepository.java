package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Optional<LeaveType> findByCodeAndActiveTrue(String code);

    List<LeaveType> findByActiveTrueOrderBySortOrderAsc();
}
