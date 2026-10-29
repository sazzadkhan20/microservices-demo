package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveBlackout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveBlackoutRepository extends JpaRepository<LeaveBlackout, Long> {

    @Query("""
            select b from LeaveBlackout b
             where (b.storeId is null or b.storeId = :storeId)
               and b.startDate <= :endDate
               and b.endDate >= :startDate
            """)
    List<LeaveBlackout> findOverlapping(@Param("storeId") String storeId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
}
