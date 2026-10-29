package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    Optional<WorkSchedule> findByScopeTypeAndScopeId(String scopeType, Long scopeId);
}
