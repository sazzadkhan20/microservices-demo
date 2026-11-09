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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;
    private final AuditService auditService;

    public NotificationService(NotificationRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Creates a notification, skipping duplicates when {@code dedupeKey} is provided.
     *
     * @return the new or existing (deduplicated) notification
     */
    public NotificationResponse create(CreateNotificationRequest req) {
        // Deduplication: if dedupeKey is set and already exists, return the existing record
        if (req.dedupeKey() != null && !req.dedupeKey().isBlank()) {
            Optional<Notification> existing =
                    repository.findByDedupeKey(req.dedupeKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        Notification n = new Notification();
        n.setEmployeeId(req.employeeId());
        n.setTitle(req.title());
        n.setBody(req.body());
        n.setCategory(req.category());
        n.setDedupeKey(req.dedupeKey());
        n.setLinkUrl(req.linkUrl());
        n.setReadFlag(false);
        n.setDeliveryAttempts(1);
        n.setLastDeliveryAt(Instant.now());
        n.setDeliveryStatus("DELIVERED");

        // uq_notif_dedupe_key rejects a concurrent duplicate; the retried command then hits the lookup above.
        Notification saved = repository.save(n);
        auditService.record(req.employeeId(), "CREATE", "Notification", String.valueOf(saved.getId()), null,
                Map.of("category", saved.getCategory() == null ? "" : saved.getCategory(),
                        "title", saved.getTitle() == null ? "" : saved.getTitle()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForEmployee(Long employeeId,
                                                       int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long employeeId) {
        return repository.countByEmployeeIdAndReadFlagFalse(employeeId);
    }

    /**
     * Marks a notification as read for the owning employee only.
     *
     * @throws IllegalArgumentException if not found
     * @throws SecurityException if the notification belongs to another employee
     */
    public NotificationResponse markReadForEmployee(Long id, Long employeeId) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notification not found: " + id));
        if (!employeeId.equals(n.getEmployeeId())) {
            throw new IllegalStateException("Forbidden: notification belongs to another employee");
        }
        n.setReadFlag(true);
        Notification saved = repository.save(n);
        auditService.record(employeeId, "MARK_READ", "Notification", String.valueOf(id), false, true);
        return toResponse(saved);
    }


    // --- mapping ---

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getEmployeeId(),
                n.getTitle(),
                n.getBody(),
                n.getCategory(),
                n.isReadFlag(),
                n.getDedupeKey(),
                n.getLinkUrl(),
                n.getCreatedAt()
        );
    }
}
