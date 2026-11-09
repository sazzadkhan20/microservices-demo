package com.platform.notification.infrastructure.persistence;

import com.platform.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    long countByEmployeeIdAndReadFlagFalse(Long employeeId);

    Optional<Notification> findByDedupeKey(String dedupeKey);

    boolean existsByDedupeKey(String dedupeKey);
}
