package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.WorkTaskAssignee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkTaskAssigneeRepository extends JpaRepository<WorkTaskAssignee, WorkTaskAssignee.Pk> {
    List<WorkTaskAssignee> findByTaskId(Long taskId);
    List<WorkTaskAssignee> findByEmployeeId(Long employeeId);
    boolean existsByTaskIdAndEmployeeId(Long taskId, Long employeeId);
    void deleteByTaskId(Long taskId);
    long countByTaskId(Long taskId);
}
