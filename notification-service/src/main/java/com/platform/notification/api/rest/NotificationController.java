package com.platform.notification.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.common.security.InternalCalls;
import com.platform.common.tenant.TenantContext;
import com.platform.notification.api.dto.CreateNotificationRequest;
import com.platform.notification.api.dto.NotificationResponse;
import com.platform.notification.application.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final String internalKey;

    public NotificationController(
            NotificationService service,
            @Value("${platform.security.internal-key:" + InternalCalls.DEFAULT_DEV_KEY + "}") String internalKey) {
        this.service = service;
        this.internalKey = internalKey;
    }

    /**
     * Lists the caller's notifications. Always own inbox (IDOR-safe).
     * Returns a dual-shape envelope: platform {@link PageResponse} fields plus Spring-style
     * {@code number}/{@code totalPages} so older FE schemas keep working.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long self = requireSelf();
        if (employeeId != null && !employeeId.equals(self)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: cannot list another employee's notifications");
        }
        String tenantId = TenantContext.getTenantId();
        // Notification-service historically used 0-based pages; keep that contract for FE.
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<NotificationResponse> result = service.listForEmployee(tenantId, self, safePage, safeSize);
        return ResponseEntity.ok(toPageMap(result, safePage, safeSize));
    }

    /**
     * Creates a notification.
     * <ul>
     *   <li>User/gateway call: always for authenticated self (body employeeId ignored).</li>
     *   <li>Internal service call ({@code X-Internal-Key}): may target body.employeeId
     *       (leave approve hook, payroll finalize, etc.).</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @Valid @RequestBody CreateNotificationRequest req,
            HttpServletRequest http) {

        String tenantId = TenantContext.getTenantId();
        Long target;
        if (InternalCalls.isTrusted(http.getHeader(InternalCalls.HEADER), internalKey)) {
            target = req.employeeId() != null ? req.employeeId() : AuthContext.getEmployeeId();
            if (target == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "employeeId is required for internal notification creates");
            }
        } else {
            target = requireSelf();
        }
        CreateNotificationRequest bound = new CreateNotificationRequest(
                target,
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

    private static Map<String, Object> toPageMap(Page<NotificationResponse> result, int page, int size) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", result.getContent());
        out.put("page", page);
        out.put("size", size);
        out.put("totalElements", result.getTotalElements());
        out.put("totalPages", result.getTotalPages());
        out.put("sort", null);
        // Spring Page aliases for existing FE springPageSchema
        out.put("number", page);
        out.put("numberOfElements", result.getNumberOfElements());
        out.put("first", result.isFirst());
        out.put("last", result.isLast());
        out.put("empty", result.isEmpty());
        return out;
    }

    private static Long requireSelf() {
        Long id = AuthContext.getEmployeeId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing employee identity");
        }
        return id;
    }
}
