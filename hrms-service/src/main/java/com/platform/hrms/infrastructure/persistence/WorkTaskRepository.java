package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.WorkTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTaskRepository extends JpaRepository<WorkTask, Long> {
}
