package com.platform.timework.infrastructure.persistence;

import com.platform.timework.domain.WorkTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTaskRepository extends JpaRepository<WorkTask, Long> {
}
