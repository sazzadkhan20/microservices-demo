package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveApprovalRepository extends JpaRepository<LeaveApproval, Long> {
    List<LeaveApproval> findByApplicationIdOrderByStepOrderAsc(Long applicationId);

    Optional<LeaveApproval> findByApplicationIdAndStepOrder(Long applicationId, int stepOrder);
}
