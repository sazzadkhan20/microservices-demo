package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {
}
