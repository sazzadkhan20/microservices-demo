package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveCategoryRepository extends JpaRepository<LeaveCategory, Long> {
    Optional<LeaveCategory> findByCodeAndActiveTrue(String code);
}
