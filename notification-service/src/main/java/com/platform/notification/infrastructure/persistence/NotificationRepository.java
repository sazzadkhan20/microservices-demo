package com.platform.notification.infrastructure.persistence;

import com.platform.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(
            String tenantId, Long employeeId, Pageable pageable);

    long countByTenantIdAndEmployeeIdAndReadFlagFalse(String tenantId, Long employeeId);

    Optional<Notification> findByTenantIdAndDedupeKey(String tenantId, String dedupeKey);

    boolean existsByTenantIdAndDedupeKey(String tenantId, String dedupeKey);

    Optional<Notification> findByIdAndTenantId(Long id, String tenantId);
}
