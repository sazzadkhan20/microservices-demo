package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveApprovalDelegate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveApprovalDelegateRepository extends JpaRepository<LeaveApprovalDelegate, Long> {

    List<LeaveApprovalDelegate> findByEmployeeIdAndActiveTrue(Long employeeId);

    List<LeaveApprovalDelegate> findByDelegateEmployeeIdAndActiveTrue(Long delegateEmployeeId);

    @Query("""
            select d from LeaveApprovalDelegate d
             where d.employeeId = :employeeId
               and d.active = true
               and d.fromDate <= :today
               and d.toDate >= :today
            """)
    Optional<LeaveApprovalDelegate> findActiveDelegate(@Param("employeeId") Long employeeId,
                                                       @Param("today") LocalDate today);

    List<LeaveApprovalDelegate> findByEmployeeId(Long employeeId);
}
