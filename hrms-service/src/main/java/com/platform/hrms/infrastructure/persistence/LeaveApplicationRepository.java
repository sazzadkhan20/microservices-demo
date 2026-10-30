package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {

    List<LeaveApplication> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<LeaveApplication> findAllByOrderByCreatedAtDesc();

    @Query("""
            select coalesce(sum(a.workingDaysCounted), 0) from LeaveApplication a
             where a.employeeId = :employeeId
               and a.status = 'APPROVED'
               and a.startDate >= :yearStart
               and a.startDate < :yearEnd
            """)
    BigDecimal sumApprovedDaysInYear(@Param("employeeId") Long employeeId,
                                     @Param("yearStart") LocalDate yearStart,
                                     @Param("yearEnd") LocalDate yearEnd);

    @Query("""
            select a from LeaveApplication a
             where a.status in ('APPROVED', 'PENDING_APPROVAL', 'PENDING_RELIEF', 'SUBMITTED')
               and a.storeId = :storeId
               and a.shiftPattern = :shift
               and a.startDate <= :day
               and a.endDate >= :day
               and a.employeeId <> :excludeEmployeeId
            """)
    List<LeaveApplication> findBlockingLeaveOnDay(@Param("storeId") String storeId,
                                                  @Param("shift") String shift,
                                                  @Param("day") LocalDate day,
                                                  @Param("excludeEmployeeId") Long excludeEmployeeId);

    @Query("""
            select a from LeaveApplication a
             where a.employeeId in :employeeIds
               and a.status in ('APPROVED', 'PENDING_APPROVAL', 'PENDING_RELIEF', 'SUBMITTED')
               and a.startDate <= :endDate
               and a.endDate >= :startDate
               and a.employeeId <> :excludeEmployeeId
            """)
    List<LeaveApplication> findOverlappingForEmployees(@Param("employeeIds") List<Long> employeeIds,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate,
                                                       @Param("excludeEmployeeId") Long excludeEmployeeId);

    @Query("""
            select a from LeaveApplication a
             where a.employeeId = :employeeId
               and a.status in ('PENDING_APPROVAL', 'APPROVED', 'PENDING_RELIEF', 'SUBMITTED')
               and a.startDate <= :endDate
               and a.endDate >= :startDate
               and (:excludeAppId is null or a.id <> :excludeAppId)
            """)
    List<LeaveApplication> findOverlappingForEmployee(@Param("employeeId") Long employeeId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate,
                                                      @Param("excludeAppId") Long excludeAppId);

    @Query("""
            select a from LeaveApplication a
             where a.status = 'PENDING_APPROVAL'
               and a.updatedAt < :cutoff
            """)
    List<LeaveApplication> findPendingApprovalOlderThan(@Param("cutoff") java.time.Instant cutoff);
}
