package com.platform.notification.application;

import com.platform.notification.api.dto.CreateNotificationRequest;
import com.platform.notification.api.dto.NotificationResponse;
import com.platform.notification.domain.Notification;
import com.platform.notification.infrastructure.persistence.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a notification, skipping duplicates when {@code dedupeKey} is provided.
     *
     * @return the new or existing (deduplicated) notification
     */
    public NotificationResponse create(String tenantId, CreateNotificationRequest req) {
        // Deduplication: if dedupeKey is set and already exists, return the existing record
        if (req.dedupeKey() != null && !req.dedupeKey().isBlank()) {
            Optional<Notification> existing =
                    repository.findByTenantIdAndDedupeKey(tenantId, req.dedupeKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setEmployeeId(req.employeeId());
        n.setTitle(req.title());
        n.setBody(req.body());
        n.setCategory(req.category());
        n.setDedupeKey(req.dedupeKey());
        n.setReadFlag(false);

        return toResponse(repository.save(n));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForEmployee(String tenantId, Long employeeId,
                                                       int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return repository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String tenantId, Long employeeId) {
        return repository.countByTenantIdAndEmployeeIdAndReadFlagFalse(tenantId, employeeId);
    }

    /**
     * Marks a notification as read for the owning employee only.
     *
     * @throws IllegalArgumentException if not found for tenant
     * @throws SecurityException if the notification belongs to another employee
     */
    public NotificationResponse markReadForEmployee(String tenantId, Long id, Long employeeId) {
        Notification n = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notification not found: " + id));
        if (!employeeId.equals(n.getEmployeeId())) {
            throw new IllegalStateException("Forbidden: notification belongs to another employee");
        }
        n.setReadFlag(true);
        return toResponse(repository.save(n));
    }


    // --- mapping ---

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTenantId(),
                n.getEmployeeId(),
                n.getTitle(),
                n.getBody(),
                n.getCategory(),
                n.isReadFlag(),
                n.getDedupeKey(),
                n.getCreatedAt()
        );
    }
}
