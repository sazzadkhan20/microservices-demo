package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.application.AuditService;
import com.platform.hrms.application.RbacService;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Onboarding/offboarding checklists — templates from tenant_settings.checklists,
 * with built-in defaults when unset (prototype parity).
 * Writes persist the whole checklists array back into tenant_settings.settings.
 * FR-CL-02/03 enhancements: ownerType, dueOffsetDays, completedBy, completedAt, assign.
 */
@RestController
@RequestMapping("/api/checklists")
@Tag(name = "Checklists")
public class ChecklistController {

    private static final String SETTINGS_KEY = "checklists";

    private final TenantSettingsRepository settingsRepository;
    private final EmployeeRepository employeeRepository;
    private final RbacService rbacService;
    private final AuditService auditService;

    public ChecklistController(TenantSettingsRepository settingsRepository,
                               EmployeeRepository employeeRepository,
                               RbacService rbacService,
                               AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.employeeRepository = employeeRepository;
        this.rbacService = rbacService;
        this.auditService = auditService;
    }

    @GetMapping
    @RequiresPermission(feature = "checklists", action = "view")
    @Operation(summary = "List onboarding/offboarding checklists")
    public List<Map<String, Object>> list() {
        return loadChecklists(settingsRepository.findAll().stream().findFirst().orElse(null));
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "checklists", action = "create")
    @Operation(summary = "Create checklist template",
            description = "Body: name, type (ONBOARDING|OFFBOARDING|custom), "
                    + "items[] of {title, required, ownerType (HR|MANAGER|EMPLOYEE), dueOffsetDays}")
    public Map<String, Object> createTemplate(@RequestBody Map<String, Object> body) {
        Object nameObj = body.get("name");
        if (nameObj == null || String.valueOf(nameObj).isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Object rawItems = body.get("items");
        if (!(rawItems instanceof List<?> itemList) || itemList.isEmpty()) {
            throw new IllegalArgumentException("items must be a non-empty list");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object it : itemList) {
            if (it instanceof Map<?, ?> m) {
                Object title = m.get("title") != null ? m.get("title") : m.get("name");
                if (title == null || String.valueOf(title).isBlank()) {
                    throw new IllegalArgumentException("each item requires a title");
                }
                boolean required = Boolean.parseBoolean(String.valueOf(m.get("required")));
                String ownerType = m.get("ownerType") != null
                        ? String.valueOf(m.get("ownerType")).toUpperCase(Locale.ROOT) : null;
                Integer dueOffsetDays = null;
                if (m.get("dueOffsetDays") instanceof Number n) {
                    dueOffsetDays = n.intValue();
                }
                items.add(templateItem(String.valueOf(title).trim(), required, ownerType, dueOffsetDays));
            } else if (it != null && !String.valueOf(it).isBlank()) {
                items.add(templateItem(String.valueOf(it).trim(), false, null, null));
            } else {
                throw new IllegalArgumentException("each item requires a title");
            }
        }
        Object typeObj = body.get("type");
        String type = typeObj == null || String.valueOf(typeObj).isBlank()
                ? "ONBOARDING" : String.valueOf(typeObj).trim().toUpperCase(Locale.ROOT);

        TenantSettings row = settingsRow();
        List<Map<String, Object>> all = loadChecklists(row);
        Map<String, Object> created = checklist(nextId(all), String.valueOf(nameObj).trim(), type, "OPEN", items);
        all.add(created);
        persist(row, all);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "Checklist",
                String.valueOf(created.get("id")), null,
                Map.of("name", created.get("name"), "type", type));
        return created;
    }

    @PutMapping("/{checklistId}/items/{itemIndex}")
    @Transactional
    @Operation(summary = "FR-CL-03 Tick/untick a checklist item; sets completedBy/completedAt when ticked, clears when unticked")
    public Map<String, Object> tickItem(@PathVariable String checklistId,
                                        @PathVariable int itemIndex,
                                        @RequestBody Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        if (!rbacService.hasPermission(actor, "checklists", "edit")
                && !rbacService.hasPermission(actor, "checklists", "create")
                && !rbacService.hasPermission(actor, "checklists", "view")) {
            throw new IllegalStateException("Forbidden: requires checklists:view/edit");
        }
        if (!(body.get("completed") instanceof Boolean completed)) {
            throw new IllegalArgumentException("completed (boolean) is required");
        }
        TenantSettings row = settingsRow();
        List<Map<String, Object>> all = loadChecklists(row);
        Map<String, Object> target = all.stream()
                .filter(c -> checklistId.equals(String.valueOf(c.get("id"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found: " + checklistId));
        List<Map<String, Object>> items = itemsOf(target);
        if (itemIndex < 0 || itemIndex >= items.size()) {
            throw new IllegalArgumentException("itemIndex out of range: " + itemIndex);
        }
        Map<String, Object> item = items.get(itemIndex);
        Object before = item.get("completed");
        item.put("completed", completed);
        if (completed) {
            // FR-CL-03: record who completed and when
            item.put("completedBy", actor);
            item.put("completedAt", Instant.now().toString());
        } else {
            // FR-CL-03: clear completion metadata when unticked
            item.remove("completedBy");
            item.remove("completedAt");
        }
        target.put("items", items);
        recount(target);
        persist(row, all);
        auditService.record(actor, "UPDATE", "Checklist", checklistId,
                Map.of("itemIndex", itemIndex, "completed", before == null ? false : before),
                Map.of("itemIndex", itemIndex, "completed", completed));
        return target;
    }

    /**
     * FR-CL-02 — Instantiate a checklist from a template and link it to an employee.
     * Body: {employeeId, type: ONBOARDING|OFFBOARDING, templateId?, startOrExitDate?}
     * Each item receives ownerEmployeeId derived from ownerType and dueDate = baseDate + dueOffsetDays.
     */
    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "checklists", action = "create")
    @Operation(summary = "FR-CL-02 Assign/instantiate a checklist for an employee from a matching template")
    public Map<String, Object> assign(@RequestBody Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();

        Object empObj = body.get("employeeId");
        if (empObj == null) throw new IllegalArgumentException("employeeId is required");
        Long employeeId = Long.valueOf(String.valueOf(empObj));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        Object typeObj = body.get("type");
        String type = typeObj == null || String.valueOf(typeObj).isBlank()
                ? "ONBOARDING" : String.valueOf(typeObj).trim().toUpperCase(Locale.ROOT);

        Object dateObj = body.get("startOrExitDate");
        LocalDate baseDate = dateObj != null && !String.valueOf(dateObj).isBlank()
                ? LocalDate.parse(String.valueOf(dateObj)) : LocalDate.now();

        TenantSettings row = settingsRow();
        List<Map<String, Object>> allChecklists = loadChecklists(row);

        // Find template: by templateId if given, else first matching type
        Map<String, Object> template = null;
        Object templateIdObj = body.get("templateId");
        if (templateIdObj != null && !String.valueOf(templateIdObj).isBlank()) {
            String tid = String.valueOf(templateIdObj);
            template = allChecklists.stream()
                    .filter(c -> tid.equals(String.valueOf(c.get("id"))) && c.get("employeeId") == null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + tid));
        } else {
            String finalType = type;
            template = allChecklists.stream()
                    .filter(c -> finalType.equalsIgnoreCase(String.valueOf(c.get("type")))
                            && c.get("employeeId") == null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No template found for type: " + type));
        }

        List<Map<String, Object>> srcItems = itemsOf(template);
        List<Map<String, Object>> assignedItems = new ArrayList<>();
        for (Map<String, Object> src : srcItems) {
            Map<String, Object> it = new LinkedHashMap<>(src);
            it.put("completed", false);
            it.remove("completedBy");
            it.remove("completedAt");

            // Resolve ownerEmployeeId from ownerType
            String ownerType = src.get("ownerType") != null
                    ? String.valueOf(src.get("ownerType")).toUpperCase(Locale.ROOT) : null;
            Long ownerEmployeeId = resolveOwner(ownerType, actor, employee);
            if (ownerEmployeeId != null) {
                it.put("ownerEmployeeId", ownerEmployeeId);
            }

            // Resolve dueDate = baseDate + dueOffsetDays
            if (src.get("dueOffsetDays") instanceof Number n) {
                it.put("dueDate", baseDate.plusDays(n.longValue()).toString());
            }
            assignedItems.add(it);
        }

        String newId = nextId(allChecklists);
        Map<String, Object> assigned = checklist(newId,
                String.valueOf(template.get("name")), type, "OPEN", assignedItems);
        assigned.put("employeeId", employeeId);
        assigned.put("baseDate", baseDate.toString());
        allChecklists.add(assigned);
        persist(row, allChecklists);

        auditService.record(actor, "CREATE", "Checklist", newId,
                null, Map.of("employeeId", employeeId, "type", type, "templateId", template.get("id")));
        return assigned;
    }

    // ---- persistence helpers -------------------------------------------------

    private Long resolveOwner(String ownerType, Long actor, Employee employee) {
        if (ownerType == null) return null;
        return switch (ownerType) {
            case "HR" -> actor;
            case "MANAGER" -> employee.getManagerId();
            case "EMPLOYEE" -> employee.getId();
            default -> null;
        };
    }

    private TenantSettings settingsRow() {
        return settingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            TenantSettings s = new TenantSettings();
            s.setUpdatedAt(Instant.now());
            return s;
        });
    }

    private void persist(TenantSettings row, List<Map<String, Object>> checklists) {
        // new map instance so Hibernate detects the JSON change
        Map<String, Object> next = new HashMap<>(row.getSettings());
        next.put(SETTINGS_KEY, checklists);
        row.setSettings(next);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
    }

    /** Deep-copy of stored checklists (or defaults when unset) so callers can mutate safely. */
    private static List<Map<String, Object>> loadChecklists(TenantSettings row) {
        Object raw = row == null ? null : row.getSettings().get(SETTINGS_KEY);
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    List<Map<String, Object>> items = itemsOf(copy);
                    copy.put("items", items);
                    recount(copy);
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

    private static void recount(Map<String, Object> checklist) {
        List<Map<String, Object>> items = itemsOf(checklist);
        long done = items.stream().filter(i -> Boolean.TRUE.equals(i.get("completed"))).count();
        checklist.put("totalItems", items.size());
        checklist.put("completedItems", done);
        checklist.put("status", !items.isEmpty() && done == items.size() ? "COMPLETED" : "OPEN");
    }

    private static String nextId(List<Map<String, Object>> all) {
        long max = 0;
        for (Map<String, Object> c : all) {
            try {
                max = Math.max(max, Long.parseLong(String.valueOf(c.get("id"))));
            } catch (NumberFormatException ignored) { /* non-numeric ids are fine */ }
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

    /** Template item — includes ownerType and dueOffsetDays; no completedBy/At. */
    private static Map<String, Object> templateItem(String title, boolean required,
                                                     String ownerType, Integer dueOffsetDays) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("required", required);
        m.put("completed", false);
        if (ownerType != null) m.put("ownerType", ownerType);
        if (dueOffsetDays != null) m.put("dueOffsetDays", dueOffsetDays);
        return m;
    }
}
