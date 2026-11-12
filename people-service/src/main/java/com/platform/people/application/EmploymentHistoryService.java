package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.EmploymentFieldHistory;
import com.platform.people.infrastructure.persistence.EmploymentFieldHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** FR-EMP-07 — records old/new/editor/date for employment field changes (history + audit log). */
@Service
public class EmploymentHistoryService {

    private final EmploymentFieldHistoryRepository repository;
    private final EmployeeRulesService rulesService;
    private final AuditService auditService;

    public EmploymentHistoryService(
            EmploymentFieldHistoryRepository repository,
            EmployeeRulesService rulesService,
            AuditService auditService) {
        this.repository = repository;
        this.rulesService = rulesService;
        this.auditService = auditService;
    }

    @Transactional
    public void record(Long employeeId, String fieldName, Object oldVal, Object newVal) {
        if (employeeId == null || fieldName == null || fieldName.isBlank()) {
            return;
        }
        String o = normalize(oldVal);
        String n = normalize(newVal);
        if (Objects.equals(o, n)) {
            return;
        }
        List<String> tracked = rulesService.load().employmentHistoryFields();
        boolean track = tracked.stream().anyMatch(f -> f.equalsIgnoreCase(fieldName));
        if (!track) {
            return;
        }
        Long actor = AuthContext.getEmployeeId();
        EmploymentFieldHistory row = new EmploymentFieldHistory();
        row.setEmployeeId(employeeId);
        row.setFieldName(fieldName);
        row.setOldValue(o);
        row.setNewValue(n);
        row.setChangedBy(actor);
        row.setChangedAt(Instant.now());
        repository.save(row);
        // Dual-write so federated audit page shows employment edits (FR-AU-01).
        auditService.record(
                actor,
                "UPDATE",
                "Employee",
                String.valueOf(employeeId),
                Map.of("field", fieldName, "value", o == null ? "" : o),
                Map.of("field", fieldName, "value", n == null ? "" : n));
    }

    @Transactional
    public void recordMany(Long employeeId, Map<String, Object> before, Map<String, Object> after) {
        if (before == null || after == null) {
            return;
        }
        for (String key : after.keySet()) {
            record(employeeId, key, before.get(key), after.get(key));
        }
        for (String key : before.keySet()) {
            if (!after.containsKey(key)) {
                record(employeeId, key, before.get(key), null);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long employeeId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EmploymentFieldHistory h : repository.findByEmployeeIdOrderByChangedAtDesc(employeeId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", h.getId());
            row.put("fieldName", h.getFieldName());
            row.put("oldValue", h.getOldValue());
            row.put("newValue", h.getNewValue());
            row.put("changedBy", h.getChangedBy());
            row.put("changedAt", h.getChangedAt() == null ? null : h.getChangedAt().toString());
            out.add(row);
        }
        return out;
    }

    private static String normalize(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
