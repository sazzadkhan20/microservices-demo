package com.platform.people.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.people.application.AuditService;
import com.platform.people.application.ChecklistAssignmentService;
import com.platform.people.application.ChecklistOwnerResolver;
import com.platform.people.application.RbacService;
import com.platform.people.domain.Employee;
import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.security.RequiresPermission;
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
import org.springframework.web.bind.annotation.RequestParam;
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
import java.util.Set;

/**
 * Onboarding/offboarding checklists — templates from tenant_settings.checklists,
 * with built-in defaults when unset.
 * FR-CL-01/02/03: templates (ownerType + dueOffsetDays), assign with named owners/due dates,
 * tick by item owner or HR pool (records completedBy / completedAt).
 */
@RestController
@RequestMapping("/api/checklists")
@Tag(name = "Checklists")
public class ChecklistController {

    private static final String SETTINGS_KEY = "checklists";
    /** HR / MANAGER / EMPLOYEE (subject) / NAMED (specific employeeId on the item). */
    private static final Set<String> OWNER_TYPES = Set.of("HR", "MANAGER", "EMPLOYEE", "NAMED");

    private final TenantSettingsRepository settingsRepository;
    private final EmployeeRepository employeeRepository;
    private final RbacService rbacService;
    private final AuditService auditService;
    private final ChecklistOwnerResolver ownerResolver;
    private final ChecklistAssignmentService assignmentService;

    public ChecklistController(TenantSettingsRepository settingsRepository,
                               EmployeeRepository employeeRepository,
                               RbacService rbacService,
                               AuditService auditService,
                               ChecklistOwnerResolver ownerResolver,
                               ChecklistAssignmentService assignmentService) {
        this.settingsRepository = settingsRepository;
        this.employeeRepository = employeeRepository;
        this.rbacService = rbacService;
        this.auditService = auditService;
        this.ownerResolver = ownerResolver;
        this.assignmentService = assignmentService;
    }

    @GetMapping
    @RequiresPermission(feature = "checklists", action = "view")
    @Transactional
    @Operation(summary = "List checklist templates and/or assigned instances",
            description = "templatesOnly=true → templates. employeeId → that subject's checklists. "
                    + "Omit employeeId → owner inbox (items you own, HR-pool items if you hold HR, "
                    + "or checklists where you are the subject).")
    public List<Map<String, Object>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false, defaultValue = "false") boolean templatesOnly) {
        Long actor = AuthContext.getEmployeeId();
        boolean manage = canManageChecklists(actor);
        // Repair legacy HR items owned by Admin/SuperAdmin (assigner) before listing.
        assignmentService.repairMisassignedHrOwners();
        List<Map<String, Object>> all = loadChecklists(settingsRepository.findAll().stream().findFirst().orElse(null));
        List<Map<String, Object>> visible = new ArrayList<>();

        if (templatesOnly) {
            if (manage) {
                for (Map<String, Object> c : all) {
                    if (c.get("employeeId") == null) {
                        visible.add(enrichChecklist(c));
                    }
                }
            }
            return visible;
        }

        // Owner inbox (any role): checklists you own an item on, hold as HR pool, or are subject of.
        if (employeeId == null) {
            for (Map<String, Object> c : all) {
                if (c.get("employeeId") == null) {
                    continue;
                }
                if (canSeeAssigned(actor, c)) {
                    visible.add(enrichChecklist(c));
                }
            }
            return visible;
        }

        Long focusId = employeeId;
        if (manage || focusId.equals(actor) || canSeeEmployeeChecklists(actor, focusId, all)) {
            for (Map<String, Object> c : all) {
                if (c.get("employeeId") == null) {
                    continue;
                }
                if (!String.valueOf(focusId).equals(String.valueOf(c.get("employeeId")))) {
                    continue;
                }
                if (manage || canSeeAssigned(actor, c)) {
                    visible.add(enrichChecklist(c));
                }
            }
        }
        return visible;
    }

    /** Manager of subject, or owns any item on that subject's checklists. */
    private boolean canSeeEmployeeChecklists(Long actor, Long focusId, List<Map<String, Object>> all) {
        if (actor == null || focusId == null) {
            return false;
        }
        Employee subject = employeeRepository.findById(focusId).orElse(null);
        if (subject != null && actor.equals(subject.getManagerId())
                && rbacService.hasPermission(actor, "checklists", "edit")) {
            return true;
        }
        for (Map<String, Object> c : all) {
            if (!String.valueOf(focusId).equals(String.valueOf(c.get("employeeId")))) {
                continue;
            }
            if (canSeeAssigned(actor, c)) {
                return true;
            }
        }
        return false;
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "checklists", action = "create")
    @Operation(summary = "Create checklist template",
            description = "Body: name, type (ONBOARDING|OFFBOARDING|CUSTOM), "
                    + "items[] of {title, required, ownerType (HR|MANAGER|EMPLOYEE|NAMED), "
                    + "ownerEmployeeId (required when NAMED), dueOffsetDays from start/exit}")
    public Map<String, Object> createTemplate(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = parseTemplateItems(body.get("items"));
        Object nameObj = body.get("name");
        if (nameObj == null || String.valueOf(nameObj).isBlank()) {
            throw new IllegalArgumentException("name is required");
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
        return enrichChecklist(created);
    }

    @PutMapping("/templates/{templateId}")
    @Transactional
    @RequiresPermission(feature = "checklists", action = "edit")
    @Operation(summary = "Update a checklist template (not an assigned instance)")
    public Map<String, Object> updateTemplate(@PathVariable String templateId,
                                              @RequestBody Map<String, Object> body) {
        TenantSettings row = settingsRow();
        List<Map<String, Object>> all = loadChecklists(row);
        Map<String, Object> target = all.stream()
                .filter(c -> templateId.equals(String.valueOf(c.get("id"))) && c.get("employeeId") == null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        Map<String, Object> before = snapshotMeta(target);

        if (body.get("name") != null && !String.valueOf(body.get("name")).isBlank()) {
            target.put("name", String.valueOf(body.get("name")).trim());
        }
        if (body.get("type") != null && !String.valueOf(body.get("type")).isBlank()) {
            target.put("type", String.valueOf(body.get("type")).trim().toUpperCase(Locale.ROOT));
        }
        if (body.get("items") != null) {
            List<Map<String, Object>> items = parseTemplateItems(body.get("items"));
            target.put("items", items);
            recount(target);
        }
        persist(row, all);
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "Checklist", templateId, before, snapshotMeta(target));
        return enrichChecklist(target);
    }

    @PutMapping("/{checklistId}/items/{itemIndex}")
    @Transactional
    @RequiresPermission(feature = "checklists", action = "view")
    @Operation(summary = "FR-CL-03 Tick/untick a checklist item (owner or HR); records completedBy/completedAt")
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
        if (target.get("employeeId") == null) {
            throw new IllegalArgumentException("Cannot tick items on a template — assign it to an employee first");
        }
        List<Map<String, Object>> items = itemsOf(target);
        if (itemIndex < 0 || itemIndex >= items.size()) {
            throw new IllegalArgumentException("itemIndex out of range: " + itemIndex);
        }
        Map<String, Object> item = items.get(itemIndex);
        assertCanTick(actor, target, item);

        Object before = item.get("completed");
        item.put("completed", completed);
        if (completed) {
            item.put("completedBy", actor);
            item.put("completedAt", Instant.now().toString());
        } else {
            item.remove("completedBy");
            item.remove("completedAt");
        }
        target.put("items", items);
        recount(target);
        persist(row, all);
        auditService.record(actor, "UPDATE", "Checklist", checklistId,
                Map.of("itemIndex", itemIndex, "completed", before == null ? false : before),
                Map.of("itemIndex", itemIndex, "completed", completed,
                        "completedBy", completed ? actor : "",
                        "completedAt", completed ? String.valueOf(item.get("completedAt")) : ""));
        return enrichChecklist(target);
    }

    /**
     * FR-CL-02 — Instantiate a checklist from a template and link it to an employee.
     * Body: {employeeId, type: ONBOARDING|OFFBOARDING|CUSTOM, templateId?, startOrExitDate?}
     * CUSTOM requires {@code templateId}. Assigned type follows the template when provided.
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
        if (!Set.of("ONBOARDING", "OFFBOARDING", "CUSTOM").contains(type)) {
            throw new IllegalArgumentException("type must be ONBOARDING, OFFBOARDING, or CUSTOM");
        }

        Object dateObj = body.get("startOrExitDate");
        if (dateObj == null || String.valueOf(dateObj).isBlank()) {
            throw new IllegalArgumentException(
                    "startOrExitDate is required (start / exit / base date for due offsets)");
        }
        LocalDate baseDate = LocalDate.parse(String.valueOf(dateObj).trim());

        TenantSettings row = settingsRow();
        List<Map<String, Object>> allChecklists = loadChecklists(row);

        Map<String, Object> template;
        Object templateIdObj = body.get("templateId");
        boolean hasTemplateId = templateIdObj != null && !String.valueOf(templateIdObj).isBlank();
        if ("CUSTOM".equals(type) && !hasTemplateId) {
            throw new IllegalArgumentException("templateId is required when type is CUSTOM");
        }
        if (hasTemplateId) {
            String tid = String.valueOf(templateIdObj);
            template = allChecklists.stream()
                    .filter(c -> tid.equals(String.valueOf(c.get("id"))) && c.get("employeeId") == null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + tid));
            // Prefer the template's own type so CUSTOM templates stay CUSTOM when assigned.
            if (template.get("type") != null && !String.valueOf(template.get("type")).isBlank()) {
                type = String.valueOf(template.get("type")).trim().toUpperCase(Locale.ROOT);
            }
        } else {
            final String typeForLookup = type;
            template = allChecklists.stream()
                    .filter(c -> typeForLookup.equalsIgnoreCase(String.valueOf(c.get("type")))
                            && c.get("employeeId") == null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No template found for type: " + typeForLookup));
        }

        List<Map<String, Object>> srcItems = itemsOf(template);
        List<Map<String, Object>> assignedItems = new ArrayList<>();
        for (Map<String, Object> src : srcItems) {
            Map<String, Object> it = new LinkedHashMap<>(src);
            it.put("completed", false);
            it.remove("completedBy");
            it.remove("completedAt");

            String ownerType = src.get("ownerType") != null
                    ? String.valueOf(src.get("ownerType")).toUpperCase(Locale.ROOT) : null;
            // HR → primary HR role holder (never the Admin/SuperAdmin assigner).
            Long ownerEmployeeId = ownerResolver.resolve(ownerType, asLong(src.get("ownerEmployeeId")), employee);
            if (ownerEmployeeId != null) {
                it.put("ownerEmployeeId", ownerEmployeeId);
            } else {
                it.remove("ownerEmployeeId");
            }

            int offset = parseDueOffsetDays(src.get("dueOffsetDays"));
            it.put("dueOffsetDays", offset);
            it.put("dueDate", baseDate.plusDays(offset).toString());
            assignedItems.add(it);
        }

        String newId = nextId(allChecklists);
        Map<String, Object> assigned = checklist(newId,
                String.valueOf(template.get("name")), type, "OPEN", assignedItems);
        assigned.put("employeeId", employeeId);
        assigned.put("baseDate", baseDate.toString());
        assigned.put("templateId", template.get("id"));
        allChecklists.add(assigned);
        persist(row, allChecklists);

        auditService.record(actor, "CREATE", "Checklist", newId,
                null, Map.of("employeeId", employeeId, "type", type, "templateId", template.get("id")));
        return enrichChecklist(assigned);
    }

    // ---- auth / visibility ---------------------------------------------------

    /**
     * Templates + tick-any: roles with {@code checklists:create} (HR/Admin) or wildcard.
     * Do not use {@code employees:edit} — some Employee seeds incorrectly hold it.
     */
    private boolean canManageChecklists(Long actor) {
        if (actor == null) return false;
        return rbacService.hasWildcard(actor)
                || rbacService.hasPermission(actor, "checklists", "create");
    }

    private boolean canSeeAssigned(Long actor, Map<String, Object> checklist) {
        if (actor == null) return false;
        if (String.valueOf(actor).equals(String.valueOf(checklist.get("employeeId")))) {
            return true;
        }
        boolean actorIsHr = ownerResolver.holdsHrRole(actor);
        for (Map<String, Object> item : itemsOf(checklist)) {
            if (String.valueOf(actor).equals(String.valueOf(item.get("ownerEmployeeId")))) {
                return true;
            }
            // Any dedicated HR role holder sees / works HR-owned items (pool), not only primary display owner.
            if (actorIsHr && isHrOwnerType(item)) {
                return true;
            }
        }
        // Manager of the subject (TEAM edit) can see the assigned checklist
        Long subjectId = asLong(checklist.get("employeeId"));
        if (subjectId != null && rbacService.hasPermission(actor, "checklists", "edit")) {
            Employee subject = employeeRepository.findById(subjectId).orElse(null);
            if (subject != null && actor.equals(subject.getManagerId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHrOwnerType(Map<String, Object> item) {
        Object ot = item.get("ownerType");
        return ot != null && "HR".equalsIgnoreCase(String.valueOf(ot).trim());
    }

    /**
     * FR-CL-03 — tick allowed for: named owner, any HR-role holder on HR items,
     * checklist managers (create), or the subject's manager (checklists:edit).
     */
    private void assertCanTick(Long actor, Map<String, Object> checklist, Map<String, Object> item) {
        Long ownerId = asLong(item.get("ownerEmployeeId"));
        if (ownerId != null && ownerId.equals(actor)) {
            return;
        }
        if (isHrOwnerType(item) && ownerResolver.holdsHrRole(actor)) {
            return;
        }
        if (canManageChecklists(actor)) {
            return;
        }
        if (rbacService.hasPermission(actor, "checklists", "edit")) {
            Long subjectId = asLong(checklist.get("employeeId"));
            if (subjectId != null) {
                Employee subject = employeeRepository.findById(subjectId).orElse(null);
                if (subject != null && actor.equals(subject.getManagerId())) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Only the item owner or HR can update this checklist item");
    }

    private Map<String, Object> enrichChecklist(Map<String, Object> c) {
        Map<String, Object> out = new LinkedHashMap<>(c);
        Long empId = asLong(c.get("employeeId"));
        if (empId != null) {
            employeeRepository.findById(empId).ifPresent(e -> {
                out.put("employeeName", e.getFullName());
                out.put("employeeCode", e.getEmployeeCode());
            });
        }
        List<Map<String, Object>> enrichedItems = new ArrayList<>();
        for (Map<String, Object> item : itemsOf(c)) {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            Long ownerId = asLong(item.get("ownerEmployeeId"));
            if (ownerId != null) {
                employeeRepository.findById(ownerId).ifPresent(e -> {
                    copy.put("ownerName", e.getFullName());
                    copy.put("ownerCode", e.getEmployeeCode());
                });
            }
            // Display hint: HR items are a role pool; primary contact is for display only.
            if (isHrOwnerType(item)) {
                copy.put("ownerPool", "HR");
                if (copy.get("ownerName") != null) {
                    copy.put("ownerDisplay", "HR · " + copy.get("ownerName"));
                } else {
                    copy.put("ownerDisplay", "HR");
                }
            } else if (copy.get("ownerName") != null) {
                copy.put("ownerDisplay", copy.get("ownerName"));
            } else if (item.get("ownerType") != null) {
                copy.put("ownerDisplay", String.valueOf(item.get("ownerType")));
            }
            Long by = asLong(item.get("completedBy"));
            if (by != null) {
                employeeRepository.findById(by).ifPresent(e -> copy.put("completedByName", e.getFullName()));
            }
            enrichedItems.add(copy);
        }
        out.put("items", enrichedItems);
        recount(out);
        return out;
    }

    // ---- persistence helpers -------------------------------------------------

    private List<Map<String, Object>> parseTemplateItems(Object rawItems) {
        if (!(rawItems instanceof List<?> itemList) || itemList.isEmpty()) {
            throw new IllegalArgumentException("items must be a non-empty list");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object it : itemList) {
            if (!(it instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("each item requires a title");
            }
            Object title = m.get("title") != null ? m.get("title") : m.get("name");
            if (title == null || String.valueOf(title).isBlank()) {
                throw new IllegalArgumentException("each item requires a title");
            }
            boolean required = m.get("required") == null || Boolean.parseBoolean(String.valueOf(m.get("required")));
            String ownerType = m.get("ownerType") != null
                    ? String.valueOf(m.get("ownerType")).trim().toUpperCase(Locale.ROOT) : null;
            if (ownerType == null || ownerType.isBlank()) {
                throw new IllegalArgumentException(
                        "each item requires ownerType (HR, MANAGER, EMPLOYEE, or NAMED)");
            }
            if (!OWNER_TYPES.contains(ownerType)) {
                throw new IllegalArgumentException("ownerType must be HR, MANAGER, EMPLOYEE, or NAMED");
            }
            Long namedOwnerId = asLong(m.get("ownerEmployeeId"));
            if ("NAMED".equals(ownerType)) {
                if (namedOwnerId == null) {
                    throw new IllegalArgumentException(
                            "ownerEmployeeId is required when ownerType is NAMED (pick the owner employee)");
                }
                Employee named = employeeRepository.findById(namedOwnerId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "ownerEmployeeId not found: " + namedOwnerId));
                if (!named.isActive()) {
                    throw new IllegalArgumentException("Named owner employee is inactive: " + namedOwnerId);
                }
            }
            int dueOffsetDays = parseDueOffsetDays(m.get("dueOffsetDays"));
            Map<String, Object> item = templateItem(String.valueOf(title).trim(), required, ownerType, dueOffsetDays);
            if ("NAMED".equals(ownerType) && namedOwnerId != null) {
                item.put("ownerEmployeeId", namedOwnerId);
            }
            items.add(item);
        }
        return items;
    }

    static int parseDueOffsetDays(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw != null && !String.valueOf(raw).isBlank()) {
            try {
                return Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("dueOffsetDays must be an integer (days from start/exit)");
            }
        }
        return 0;
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

    private static List<Map<String, Object>> loadChecklists(TenantSettings row) {
        Object raw = row == null || row.getSettings() == null ? null : row.getSettings().get(SETTINGS_KEY);
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

    private static Map<String, Object> snapshotMeta(Map<String, Object> c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.get("name"));
        m.put("type", c.get("type"));
        m.put("totalItems", c.get("totalItems"));
        return m;
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
}
