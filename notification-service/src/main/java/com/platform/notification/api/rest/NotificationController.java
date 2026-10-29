package com.platform.notification.api.rest;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * Lists the caller's notifications. Other employees' inboxes require notifications:manage
     * (not exposed here — always own inbox).
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long self = requireSelf();
        if (employeeId != null && !employeeId.equals(self)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: cannot list another employee's notifications");
        }
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(service.listForEmployee(tenantId, self, page, size));
    }

    /**
     * Creates a notification for the authenticated employee only.
     * Internal producers (leave hook) should use the same gateway path with the target
     * employee's JWT/service identity, or a dedicated internal endpoint later.
     * Body {@code employeeId} is ignored to prevent IDOR writes.
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @Valid @RequestBody CreateNotificationRequest req) {

        Long self = requireSelf();
        String tenantId = TenantContext.getTenantId();
        CreateNotificationRequest bound = new CreateNotificationRequest(
                self,
                req.title(),
                req.body(),
                req.category(),
                req.dedupeKey()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, bound));
    }

    /** Marks own notification as read. */
    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        Long self = requireSelf();
        return ResponseEntity.ok(service.markReadForEmployee(tenantId, id, self));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @RequestParam(required = false) Long employeeId) {

        Long self = requireSelf();
        if (employeeId != null && !employeeId.equals(self)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: cannot read another employee's unread count");
        }
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(Map.of("unreadCount", service.unreadCount(tenantId, self)));
    }

    private static Long requireSelf() {
        Long id = AuthContext.getEmployeeId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing employee identity");
        }
        return id;
    }
}
