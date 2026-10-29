package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.application.AuditService;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.security.RequiresPermission;
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

    public EmployeeDocumentController(EmployeeRepository employeeRepository, AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "List document metadata for an employee")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(@PathVariable Long id) {
        return documentsOf(requireEmployee(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "employees", action = "edit")
    @Operation(summary = "Attach uploaded file metadata (fileId, documentType, name)")
    @Transactional
    public Map<String, Object> add(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Employee employee = requireEmployee(id);
        String fileId = requireText(body, "fileId");
        String type = body.get("documentType") != null ? text(body.get("documentType")) : text(body.get("type"));
        if (type == null) {
            throw new IllegalArgumentException("documentType is required");
        }
        String name = requireText(body, "name");

        List<Map<String, Object>> docs = documentsOf(employee);
        if (docs.stream().anyMatch(d -> fileId.equals(String.valueOf(d.get("fileId"))))) {
            throw new IllegalStateException("Document already attached: " + fileId);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("fileId", fileId);
        doc.put("type", type);
        doc.put("name", name);
        doc.put("uploadedAt", Instant.now().toString());
        Long actor = AuthContext.getEmployeeId();
        if (actor != null) {
            doc.put("uploadedBy", actor);
        }
        docs.add(doc);
        save(employee, docs);
        auditService.record(actor, "CREATE", "EmployeeDocument", id + ":" + fileId, null, doc);
        return doc;
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(feature = "employees", action = "edit")
    @Operation(summary = "Remove document metadata by fileId")
    @Transactional
    public void remove(@PathVariable Long id, @PathVariable String fileId) {
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
        // strip derived alias before persisting; new map instance so Hibernate detects the JSON change
        List<Map<String, Object>> clean = new ArrayList<>();
        for (Map<String, Object> d : docs) {
            Map<String, Object> c = new LinkedHashMap<>(d);
            c.remove("documentType");
            clean.add(c);
        }
        Map<String, Object> next = new HashMap<>(employee.getCustomFields() == null ? Map.of() : employee.getCustomFields());
        next.put(KEY, clean);
        employee.setCustomFields(next);
        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);
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
