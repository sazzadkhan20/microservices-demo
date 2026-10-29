package com.platform.notification.api.rest;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.AuthContext;
import com.platform.common.tenant.TenantContext;
import com.platform.notification.api.dto.CreateNotificationRequest;
import com.platform.notification.api.dto.NotificationResponse;
import com.platform.notification.application.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * Lists notifications for an employee, newest first.
     * employeeId defaults to X-Employee-ID from the gateway JWT context.
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long resolvedEmployee = resolveEmployee(employeeId);
        String tenantId = TenantContext.getTenantId();
        Page<NotificationResponse> result = service.listForEmployee(tenantId, resolvedEmployee, page, size);
        return ResponseEntity.ok(result);
    }

    /** Creates a new notification; deduplication is applied when dedupeKey is set. */
    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @Valid @RequestBody CreateNotificationRequest req) {

        String tenantId = TenantContext.getTenantId();
        NotificationResponse response = service.create(tenantId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Marks a notification as read (tenant-scoped for safety). */
    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(service.markRead(tenantId, id));
    }

    /**
     * Returns the unread notification count for an employee.
     * employeeId defaults to X-Employee-ID from the gateway JWT context.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @RequestParam(required = false) Long employeeId) {

        Long resolvedEmployee = resolveEmployee(employeeId);
        String tenantId = TenantContext.getTenantId();
        long count = service.unreadCount(tenantId, resolvedEmployee);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // --- helpers ---

    private Long resolveEmployee(Long explicit) {
        if (explicit != null) {
            return explicit;
        }
        Long fromContext = AuthContext.getEmployeeId();
        if (fromContext == null) {
            throw new IllegalArgumentException(
                    "employeeId query param required when X-Employee-ID header is absent");
        }
        return fromContext;
    }
}
