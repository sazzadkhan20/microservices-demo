package com.platform.leave.infrastructure.persistence;

import com.platform.leave.domain.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {
}
