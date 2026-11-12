package com.platform.people.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.people.application.AuditService;
import com.platform.people.application.EmployeeRulesService;
import com.platform.people.client.FilesGatewayClient;
import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Employee document metadata (file bytes live in file-service).
 * Stored in employee.custom_fields.documents as [{fileId, type, name, uploadedAt, uploadedBy}].
 */
@RestController
@RequestMapping("/api/employees/{id}/documents")
@Tag(name = "Employee Documents", description = "Document metadata linked to file-service uploads")
public class EmployeeDocumentController {

    private static final String KEY = "documents";

    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final EmployeeRulesService employeeRulesService;
    private final FilesGatewayClient filesGatewayClient;
    private final com.platform.people.application.DataScopeService dataScopeService;

    public EmployeeDocumentController(
            EmployeeRepository employeeRepository,
            AuditService auditService,
            EmployeeRulesService employeeRulesService,
            FilesGatewayClient filesGatewayClient,
            com.platform.people.application.DataScopeService dataScopeService) {
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.employeeRulesService = employeeRulesService;
        this.filesGatewayClient = filesGatewayClient;
        this.dataScopeService = dataScopeService;
    }

    /**
     * Guide §4 — feature permission AND record scope. The interceptor only proves the feature
     * ({@code employees:view|edit}); this proves the target employee is inside the caller's scope:
     * self, the {@code employees:<action>} data scope (manager → team, HR → all), or an explicit
     * {@code documents:<docAction>} grant.
     */
    private void requireDocumentScope(Long employeeId, String employeesAction, String documentsAction) {
        Long actor = AuthContext.requireEmployeeId();
        if (actor.equals(employeeId)
                || dataScopeService.canAccess(actor, employeeId, "employees", employeesAction)
                || dataScopeService.canAccess(actor, employeeId, "documents", documentsAction)) {
            return;
        }
        throw new SecurityException("Forbidden: documents of employee " + employeeId + " are outside your access scope");
    }

    @GetMapping
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "List document metadata for an employee (includes live file-service scanStatus)")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(@PathVariable Long id) {
        requireDocumentScope(id, "view", "view");
        return enrichScanState(documentsOf(requireEmployee(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "employees", action = "edit")
    @Operation(summary = "Attach uploaded file metadata (fileId, documentType, name)")
    @Transactional
    public Map<String, Object> add(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireDocumentScope(id, "edit", "create");
        Employee employee = requireEmployee(id);
        String fileId = requireText(body, "fileId");
        String type = body.get("documentType") != null ? text(body.get("documentType")) : text(body.get("type"));
        if (type == null) {
            throw new IllegalArgumentException("documentType is required");
        }
        employeeRulesService.assertDocumentType(type, employeeRulesService.load());
        // Guide §8: validate file existence (and reject infected) before creating the link.
        Long parsedFileId;
        try {
            parsedFileId = Long.parseLong(fileId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("fileId must be numeric");
        }
        filesGatewayClient.assertAttachable(parsedFileId);
        // Guide §8 — bind file to this employee so subject + HR can resolve scan/download ACL.
        filesGatewayClient.bindEmployee(parsedFileId, id);
        String name = requireText(body, "name");
        String expiresAt = text(body.get("expiresAt"));

        List<Map<String, Object>> docs = documentsOf(employee);
        if (docs.stream().anyMatch(d -> fileId.equals(String.valueOf(d.get("fileId"))))) {
            throw new IllegalStateException("Document already attached: " + fileId);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("fileId", fileId);
        doc.put("type", type);
        doc.put("name", name);
        if (expiresAt != null) {
            doc.put("expiresAt", expiresAt);
        }
        doc.put("uploadedAt", Instant.now().toString());
        Long actor = AuthContext.getEmployeeId();
        if (actor != null) {
            doc.put("uploadedBy", actor);
        }
        putScanFields(doc, parsedFileId);
        docs.add(doc);
        save(employee, docs);
        auditService.record(actor, "CREATE", "EmployeeDocument", id + ":" + fileId, null, doc);
        return enrichScanState(List.of(doc)).get(0);
    }

    @PostMapping("/{fileId}/access")
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "Log document open/download (FR-EMP-08)")
    @Transactional
    public Map<String, Object> logAccess(
            @PathVariable Long id,
            @PathVariable String fileId,
            @RequestBody(required = false) Map<String, Object> body) {
        requireDocumentScope(id, "view", "view");
        Employee employee = requireEmployee(id);
        Map<String, Object> doc = documentsOf(employee).stream()
                .filter(d -> fileId.equals(String.valueOf(d.get("fileId"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + fileId));
        String action = body != null && body.get("action") != null
                ? String.valueOf(body.get("action")).trim().toUpperCase()
                : "DOWNLOAD";
        if ("VIEW".equals(action)) {
            action = "OPEN";
        }
        if (!"OPEN".equals(action) && !"DOWNLOAD".equals(action)) {
            action = "DOWNLOAD";
        }
        Long actor = AuthContext.getEmployeeId();
        Map<String, Object> after = new LinkedHashMap<>(doc);
        after.put("accessAction", action);
        after.put("accessedAt", Instant.now().toString());
        auditService.record(actor, action, "EmployeeDocument", id + ":" + fileId, doc, after);
        return Map.of("ok", true, "action", action, "fileId", fileId);
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(feature = "employees", action = "edit")
    @Operation(summary = "Remove document metadata by fileId")
    @Transactional
    public void remove(@PathVariable Long id, @PathVariable String fileId) {
        requireDocumentScope(id, "edit", "delete");
        Employee employee = requireEmployee(id);
        List<Map<String, Object>> docs = documentsOf(employee);
        Map<String, Object> removed = docs.stream()
                .filter(d -> fileId.equals(String.valueOf(d.get("fileId"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + fileId));
        docs.remove(removed);
        save(employee, docs);
        auditService.record(AuthContext.getEmployeeId(), "DELETE", "EmployeeDocument", id + ":" + fileId, removed, null);
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));
    }

    private static List<Map<String, Object>> documentsOf(Employee employee) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object raw = employee.getCustomFields() == null ? null : employee.getCustomFields().get(KEY);
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    copy.putIfAbsent("documentType", copy.get("type"));
                    out.add(copy);
                }
            }
        }
        return out;
    }

    private void save(Employee employee, List<Map<String, Object>> docs) {
        // strip derived alias / live scan fields before persisting; new map so Hibernate detects JSON change
        List<Map<String, Object>> clean = new ArrayList<>();
        for (Map<String, Object> d : docs) {
            Map<String, Object> c = new LinkedHashMap<>(d);
            c.remove("documentType");
            c.remove("scanStatus");
            c.remove("downloadable");
            c.remove("scanDetail");
            c.remove("scannedAt");
            c.remove("releasedAt");
            clean.add(c);
        }
        Map<String, Object> next = new HashMap<>(employee.getCustomFields() == null ? Map.of() : employee.getCustomFields());
        next.put(KEY, clean);
        employee.setCustomFields(next);
        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);
    }

    /** Live scan fields from file-service (guide §8) — not stored on the employee JSON. */
    private List<Map<String, Object>> enrichScanState(List<Map<String, Object>> docs) {
        List<Map<String, Object>> out = new ArrayList<>(docs.size());
        for (Map<String, Object> d : docs) {
            Map<String, Object> copy = new LinkedHashMap<>(d);
            Object rawId = copy.get("fileId");
            if (rawId != null && !String.valueOf(rawId).isBlank()) {
                try {
                    putScanFields(copy, Long.parseLong(String.valueOf(rawId).trim()));
                } catch (Exception ignored) {
                    copy.putIfAbsent("scanStatus", "UNKNOWN");
                    copy.putIfAbsent("downloadable", false);
                }
            }
            out.add(copy);
        }
        return out;
    }

    private void putScanFields(Map<String, Object> doc, Long fileId) {
        try {
            Map<String, Object> desc = filesGatewayClient.describe(fileId);
            if (desc.get("scanStatus") != null) {
                doc.put("scanStatus", desc.get("scanStatus"));
            }
            if (desc.get("downloadable") != null) {
                doc.put("downloadable", desc.get("downloadable"));
            }
            if (desc.get("scanDetail") != null) {
                doc.put("scanDetail", desc.get("scanDetail"));
            }
            if (desc.get("scannedAt") != null) {
                doc.put("scannedAt", desc.get("scannedAt"));
            }
            if (desc.get("releasedAt") != null) {
                doc.put("releasedAt", desc.get("releasedAt"));
            }
        } catch (RuntimeException ex) {
            doc.putIfAbsent("scanStatus", "UNKNOWN");
            doc.putIfAbsent("downloadable", false);
        }
    }

    private static String requireText(Map<String, Object> body, String key) {
        String v = text(body.get(key));
        if (v == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v;
    }

    private static String text(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v).trim();
    }
}
