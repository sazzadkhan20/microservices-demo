package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveApprovalRepository extends JpaRepository<LeaveApproval, Long> {
    List<LeaveApproval> findByApplicationIdOrderByStepOrderAsc(Long applicationId);

    /** Prefer list — cancel rebuild must not blow up if stale duplicate steps exist. */
    List<LeaveApproval> findByApplicationIdAndStepOrderOrderByIdAsc(Long applicationId, int stepOrder);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LeaveApproval a where a.applicationId = :applicationId")
    int deleteByApplicationId(@Param("applicationId") Long applicationId);
}
