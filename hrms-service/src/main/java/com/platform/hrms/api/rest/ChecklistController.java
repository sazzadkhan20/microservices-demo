package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.application.AuditService;
import com.platform.hrms.application.RbacService;
import com.platform.hrms.domain.TenantSettings;
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
 */
@RestController
@RequestMapping("/api/checklists")
@Tag(name = "Checklists")
public class ChecklistController {

    private static final String SETTINGS_KEY = "checklists";

    private final TenantSettingsRepository settingsRepository;
    private final RbacService rbacService;
    private final AuditService auditService;

    public ChecklistController(TenantSettingsRepository settingsRepository,
                               RbacService rbacService,
                               AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.rbacService = rbacService;
        this.auditService = auditService;
    }

    @GetMapping
    @RequiresPermission(feature = "org", action = "view")
    @Operation(summary = "List onboarding/offboarding checklists")
    public List<Map<String, Object>> list() {
        return loadChecklists(settingsRepository.findAll().stream().findFirst().orElse(null));
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "org", action = "manage")
    @Operation(summary = "Create checklist template",
            description = "Body: name, type (ONBOARDING|OFFBOARDING|custom), items[] of {title, required} or plain strings")
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
                items.add(item(String.valueOf(title).trim(), Boolean.parseBoolean(String.valueOf(m.get("required"))), false));
            } else if (it != null && !String.valueOf(it).isBlank()) {
                items.add(item(String.valueOf(it).trim(), false, false));
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
                String.valueOf(created.get("id")), null, Map.of("name", created.get("name"), "type", type));
        return created;
    }

    @PutMapping("/{checklistId}/items/{itemIndex}")
    @Transactional
    @Operation(summary = "Tick / untick a checklist item (body {completed:boolean}); org:view or org:manage")
    public Map<String, Object> tickItem(@PathVariable String checklistId,
                                        @PathVariable int itemIndex,
                                        @RequestBody Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        if (!rbacService.hasPermission(actor, "org", "view") && !rbacService.hasPermission(actor, "org", "manage")) {
            throw new IllegalStateException("Forbidden: requires org:view or org:manage");
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
        target.put("items", items);
        recount(target);
        persist(row, all);
        auditService.record(actor, "UPDATE", "Checklist", checklistId,
                Map.of("itemIndex", itemIndex, "completed", before == null ? false : before),
                Map.of("itemIndex", itemIndex, "completed", completed));
        return target;
    }

    // ---- persistence helpers -------------------------------------------------

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
                                item("Laptop / workstation ready", true, false),
                                item("Email & SSO account created", true, false),
                                item("HRMS profile + role assigned", true, false),
                                item("Team introduction scheduled", false, false),
                                item("Policy acknowledgment signed", true, false)
                        )),
                checklist("2", "Employee Offboarding", "OFFBOARDING", "OPEN",
                        List.of(
                                item("Asset return checklist", true, false),
                                item("Access revoked (email/SSO/VPN)", true, false),
                                item("Final payroll / leave settlement", true, false),
                                item("Exit interview completed", false, false)
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

    private static Map<String, Object> item(String title, boolean required, boolean completed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("required", required);
        m.put("completed", completed);
        return m;
    }
}
