package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.Employee;
import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FR-CL-02 / FR-EMP create — instantiate an onboarding/offboarding checklist from a template
 * and persist it under tenant_settings.checklists linked to the employee.
 */
@Service
public class ChecklistAssignmentService {

    private static final String SETTINGS_KEY = "checklists";

    private final TenantSettingsRepository settingsRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final ChecklistOwnerResolver ownerResolver;

    public ChecklistAssignmentService(
            TenantSettingsRepository settingsRepository,
            EmployeeRepository employeeRepository,
            AuditService auditService,
            ChecklistOwnerResolver ownerResolver) {
        this.settingsRepository = settingsRepository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.ownerResolver = ownerResolver;
    }

    @Transactional
    public Map<String, Object> assign(Long employeeId, String type, LocalDate baseDate) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        Long actor = AuthContext.getEmployeeId();
        String resolvedType = type == null || type.isBlank() ? "ONBOARDING" : type.trim().toUpperCase(Locale.ROOT);
        LocalDate base = baseDate == null ? LocalDate.now() : baseDate;

        TenantSettings row = settingsRow();
        List<Map<String, Object>> all = loadChecklists(row);
        Map<String, Object> template = all.stream()
                .filter(c -> resolvedType.equalsIgnoreCase(String.valueOf(c.get("type")))
                        && c.get("employeeId") == null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No template found for type: " + resolvedType));

        List<Map<String, Object>> assignedItems = new ArrayList<>();
        for (Map<String, Object> src : itemsOf(template)) {
            Map<String, Object> it = new LinkedHashMap<>(src);
            it.put("completed", false);
            it.remove("completedBy");
            it.remove("completedAt");
            String ownerType = src.get("ownerType") != null
                    ? String.valueOf(src.get("ownerType")).toUpperCase(Locale.ROOT) : null;
            Long namedId = asLong(src.get("ownerEmployeeId"));
            // NAMED keeps template ownerEmployeeId; HR/MANAGER/EMPLOYEE resolve from org, not assigner.
            Long ownerEmployeeId = ownerResolver.resolve(ownerType, namedId, employee);
            if (ownerEmployeeId != null) {
                it.put("ownerEmployeeId", ownerEmployeeId);
            } else {
                it.remove("ownerEmployeeId");
            }
            int offset = parseDueOffsetDays(src.get("dueOffsetDays"));
            it.put("dueOffsetDays", offset);
            it.put("dueDate", base.plusDays(offset).toString());
            assignedItems.add(it);
        }

        String newId = nextId(all);
        Map<String, Object> assigned = checklist(newId,
                String.valueOf(template.get("name")), resolvedType, "OPEN", assignedItems);
        assigned.put("employeeId", employeeId);
        assigned.put("baseDate", base.toString());
        assigned.put("templateId", template.get("id"));
        all.add(assigned);
        persist(row, all);

        auditService.record(actor, "CREATE", "Checklist", newId,
                null, Map.of("employeeId", employeeId, "type", resolvedType, "templateId", template.get("id")));
        return assigned;
    }

    /** Best-effort onboarding attach — never fails employee create if no template exists. */
    @Transactional
    public void assignOnboardingBestEffort(Long employeeId, LocalDate startDate) {
        try {
            assign(employeeId, "ONBOARDING", startDate);
        } catch (IllegalArgumentException ignored) {
            // no template yet — HR can assign later
        }
    }

    /**
     * FR-CL-02 — attach offboarding checklist when offboarding starts (idempotent if already assigned).
     * Never fails the status change if no template exists.
     */
    @Transactional
    public void assignOffboardingBestEffort(Long employeeId, LocalDate exitDate) {
        if (hasAssignedChecklist(employeeId, "OFFBOARDING")) {
            return;
        }
        try {
            assign(employeeId, "OFFBOARDING", exitDate);
        } catch (IllegalArgumentException ignored) {
            // no template yet — HR can assign later
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAssignedChecklist(Long employeeId, String type) {
        if (employeeId == null || type == null) {
            return false;
        }
        String want = type.trim().toUpperCase(Locale.ROOT);
        TenantSettings row = settingsRepository.findAll().stream().findFirst().orElse(null);
        for (Map<String, Object> c : loadChecklists(row)) {
            if (c.get("employeeId") == null) {
                continue;
            }
            if (!want.equalsIgnoreCase(String.valueOf(c.get("type")))) {
                continue;
            }
            if (String.valueOf(employeeId).equals(String.valueOf(c.get("employeeId")))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fix assigned HR items that were wrongly owned by Admin/SuperAdmin (assigner).
     * Idempotent — returns number of items repaired.
     */
    @Transactional
    public int repairMisassignedHrOwners() {
        TenantSettings row = settingsRow();
        List<Map<String, Object>> all = loadChecklists(row);
        Long primaryHr = ownerResolver.primaryHrEmployeeId();
        if (primaryHr == null) {
            return 0;
        }
        int fixed = 0;
        boolean changed = false;
        for (Map<String, Object> c : all) {
            if (c.get("employeeId") == null) {
                continue;
            }
            List<Map<String, Object>> items = itemsOf(c);
            boolean itemChanged = false;
            for (Map<String, Object> item : items) {
                String ownerType = item.get("ownerType") == null
                        ? "" : String.valueOf(item.get("ownerType")).trim().toUpperCase(Locale.ROOT);
                if (!"HR".equals(ownerType)) {
                    continue;
                }
                Long ownerId = asLong(item.get("ownerEmployeeId"));
                if (ownerResolver.needsHrOwnerRepair(ownerId)) {
                    item.put("ownerEmployeeId", primaryHr);
                    fixed++;
                    itemChanged = true;
                }
            }
            if (itemChanged) {
                c.put("items", items);
                changed = true;
            }
        }
        if (changed) {
            persist(row, all);
        }
        return fixed;
    }

    static int parseDueOffsetDays(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw != null && !String.valueOf(raw).isBlank()) {
            try {
                return Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TenantSettings settingsRow() {
        return settingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            TenantSettings s = new TenantSettings();
            s.setUpdatedAt(Instant.now());
            return s;
        });
    }

    private void persist(TenantSettings row, List<Map<String, Object>> checklists) {
        Map<String, Object> next = new HashMap<>(row.getSettings() == null ? Map.of() : row.getSettings());
        next.put(SETTINGS_KEY, checklists);
        row.setSettings(next);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadChecklists(TenantSettings row) {
        Object raw = row == null || row.getSettings() == null ? null : row.getSettings().get(SETTINGS_KEY);
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    copy.put("items", itemsOf(copy));
                    out.add(copy);
                }
            }
            return out;
        }
        return new ArrayList<>(defaultTemplates());
    }

    private static List<Map<String, Object>> itemsOf(Map<String, Object> checklist) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (checklist.get("items") instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> im) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    im.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    items.add(copy);
                }
            }
        }
        return items;
    }

    private static String nextId(List<Map<String, Object>> all) {
        long max = 0;
        for (Map<String, Object> c : all) {
            try {
                max = Math.max(max, Long.parseLong(String.valueOf(c.get("id"))));
            } catch (NumberFormatException ignored) {
                /* non-numeric */
            }
        }
        return String.valueOf(max + 1);
    }

    private static List<Map<String, Object>> defaultTemplates() {
        return List.of(
                checklist("1", "New Hire Onboarding", "ONBOARDING", "OPEN",
                        List.of(
                                templateItem("Laptop / workstation ready", true, "HR", 0),
                                templateItem("Email & SSO account created", true, "HR", 1),
                                templateItem("HRMS profile + role assigned", true, "HR", 2),
                                templateItem("Team introduction scheduled", false, "MANAGER", 3),
                                templateItem("Policy acknowledgment signed", true, "EMPLOYEE", 7)
                        )),
                checklist("2", "Employee Offboarding", "OFFBOARDING", "OPEN",
                        List.of(
                                templateItem("Asset return checklist", true, "EMPLOYEE", 0),
                                templateItem("Access revoked (email/SSO/VPN)", true, "HR", 1),
                                templateItem("Final payroll / leave settlement", true, "HR", 3),
                                templateItem("Exit interview completed", false, "MANAGER", -7)
                        ))
        );
    }

    private static Map<String, Object> checklist(String id, String name, String type, String status,
                                                 List<Map<String, Object>> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type);
        m.put("status", status);
        m.put("items", new ArrayList<>(items));
        m.put("totalItems", items.size());
        m.put("completedItems", items.stream().filter(i -> Boolean.TRUE.equals(i.get("completed"))).count());
        return m;
    }

    private static Map<String, Object> templateItem(String title, boolean required,
                                                    String ownerType, Integer dueOffsetDays) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("required", required);
        m.put("completed", false);
        if (ownerType != null) {
            m.put("ownerType", ownerType);
        }
        if (dueOffsetDays != null) {
            m.put("dueOffsetDays", dueOffsetDays);
        }
        return m;
    }
}
