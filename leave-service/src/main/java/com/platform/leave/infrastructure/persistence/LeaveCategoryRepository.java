package com.platform.leave.infrastructure.persistence;

import com.platform.leave.domain.LeaveCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveCategoryRepository extends JpaRepository<LeaveCategory, Long> {
    List<LeaveCategory> findByCodeIgnoreCase(String code);
}
