package com.platform.timework.infrastructure.persistence;

import com.platform.timework.domain.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    Optional<WorkSchedule> findByScopeTypeAndScopeId(String scopeType, Long scopeId);
}
