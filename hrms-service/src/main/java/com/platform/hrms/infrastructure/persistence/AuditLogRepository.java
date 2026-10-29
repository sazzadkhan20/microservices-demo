package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
