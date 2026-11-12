package com.platform.people.application;

import com.platform.people.domain.Employee;
import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FR-NT daily sweep: contract/probation ending in 14 days, docs expiring in 30 days,
 * checklist items due in 2 days or overdue.
 */
@Service
public class PeopleLifecycleNotifyService {

    private static final Logger log = LoggerFactory.getLogger(PeopleLifecycleNotifyService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private final EmployeeRepository employeeRepository;
    private final TenantSettingsRepository settingsRepository;
    private final RbacService rbacService;
    private final PeopleNotificationHook notificationHook;

    public PeopleLifecycleNotifyService(
            EmployeeRepository employeeRepository,
            TenantSettingsRepository settingsRepository,
            RbacService rbacService,
            PeopleNotificationHook notificationHook) {
        this.employeeRepository = employeeRepository;
        this.settingsRepository = settingsRepository;
        this.rbacService = rbacService;
        this.notificationHook = notificationHook;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> runAll() {
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", today.toString());
        out.put("contractEnd", notifyContractEnds(today));
        out.put("docExpiry", notifyDocExpiry(today));
        out.put("checklistDue", notifyChecklistDue(today));
        return out;
    }

    private int notifyContractEnds(LocalDate today) {
        LocalDate horizon = today.plusDays(14);
        Set<Long> hrIds = hrEmployeeIds();
        int sent = 0;
        for (Employee e : employeeRepository.findAll()) {
            if (e == null || e.getId() == null || !isActive(e)) continue;
            sent += notifyEndIfInWindow(e, e.getProbationEnd(), "Probation", today, horizon, hrIds);
            sent += notifyEndIfInWindow(e, e.getContractEnd(), contractKind(e), today, horizon, hrIds);
        }
        return sent;
    }

    private int notifyEndIfInWindow(
            Employee e, LocalDate end, String kind, LocalDate today, LocalDate horizon, Set<Long> hrIds) {
        if (end == null || end.isBefore(today) || end.isAfter(horizon)) {
            return 0;
        }
        int n = 0;
        Set<Long> recipients = new HashSet<>();
        if (e.getManagerId() != null) {
            recipients.add(e.getManagerId());
        }
        recipients.addAll(hrIds);
        recipients.remove(e.getId());
        for (Long rid : recipients) {
            if (notificationHook.notifyContractEnding(rid, e.getId(), e.getFullName(), kind, end.toString())) {
                n++;
            }
        }
        return n;
    }

    private String contractKind(Employee e) {
        String type = e.getEmploymentType() == null ? "" : e.getEmploymentType().trim().toUpperCase(Locale.ROOT);
        if (type.contains("INTERN")) return "Internship";
        if (type.contains("CONTRACT")) return "Contract";
        return "Contract";
    }

    @SuppressWarnings("unchecked")
    private int notifyDocExpiry(LocalDate today) {
        LocalDate horizon = today.plusDays(30);
        Set<Long> hrIds = hrEmployeeIds();
        if (hrIds.isEmpty()) return 0;
        int sent = 0;
        for (Employee e : employeeRepository.findAll()) {
            if (e == null || e.getId() == null || !isActive(e)) continue;
            List<Map<String, Object>> docs = documentsOf(e);
            for (Map<String, Object> doc : docs) {
                String expiresRaw = text(doc.get("expiresAt"));
                if (expiresRaw == null) continue;
                LocalDate expires;
                try {
                    expires = LocalDate.parse(expiresRaw.length() >= 10 ? expiresRaw.substring(0, 10) : expiresRaw);
                } catch (Exception ex) {
                    continue;
                }
                if (expires.isBefore(today) || expires.isAfter(horizon)) continue;
                String docName = text(doc.get("name"));
                if (docName == null) docName = text(doc.get("type"));
                for (Long hrId : hrIds) {
                    if (notificationHook.notifyDocExpiring(hrId, e.getId(), e.getFullName(), docName, expires.toString())) {
                        sent++;
                    }
                }
            }
        }
        return sent;
    }

    @SuppressWarnings("unchecked")
    private int notifyChecklistDue(LocalDate today) {
        LocalDate soon = today.plusDays(2);
        TenantSettings row = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (row == null || row.getSettings() == null) return 0;
        Object raw = row.getSettings().get("checklists");
        if (!(raw instanceof List<?> list)) return 0;
        int sent = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> cm)) continue;
            Map<String, Object> checklist = cast(cm);
            if (checklist.get("employeeId") == null) continue; // skip templates
            Long employeeId = asLong(checklist.get("employeeId"));
            String status = text(checklist.get("status"));
            if (status != null && "COMPLETED".equalsIgnoreCase(status)) continue;
            String checklistName = text(checklist.get("name"));
            Object itemsRaw = checklist.get("items");
            if (!(itemsRaw instanceof List<?> items)) continue;
            String checklistId = text(checklist.get("id"));
            int index = 0;
            for (Object it : items) {
                int itemIndex = index++;
                if (!(it instanceof Map<?, ?> im)) continue;
                Map<String, Object> item = cast(im);
                if (Boolean.TRUE.equals(item.get("completed"))) continue;
                String dueRaw = text(item.get("dueDate"));
                if (dueRaw == null) continue;
                LocalDate due;
                try {
                    due = LocalDate.parse(dueRaw.length() >= 10 ? dueRaw.substring(0, 10) : dueRaw);
                } catch (Exception ex) {
                    continue;
                }
                boolean overdue = due.isBefore(today);
                boolean dueSoon = !overdue && !due.isAfter(soon);
                if (!overdue && !dueSoon) continue;
                Long ownerId = asLong(item.get("ownerEmployeeId"));
                if (ownerId == null) continue;
                String label = text(item.get("label"));
                if (label == null) label = text(item.get("title"));
                if (label == null) label = text(item.get("name"));
                if (notificationHook.notifyChecklistDue(
                        ownerId,
                        employeeId,
                        checklistId,
                        itemIndex,
                        checklistName,
                        label,
                        due.toString(),
                        overdue)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    private Set<Long> hrEmployeeIds() {
        Set<Long> out = new HashSet<>();
        for (Employee e : employeeRepository.findAll()) {
            if (e == null || e.getId() == null || !isActive(e)) continue;
            boolean hr = rbacService.roleCodesFor(e.getId()).stream()
                    .anyMatch(c -> c != null && ("HR".equalsIgnoreCase(c)
                            || "ADMIN".equalsIgnoreCase(c)
                            || "SUPERADMIN".equalsIgnoreCase(c)));
            if (hr) out.add(e.getId());
        }
        return out;
    }

    private static boolean isActive(Employee e) {
        if (e.getStatus() == null) return true;
        String s = e.getStatus().trim().toUpperCase(Locale.ROOT);
        return !(s.contains("INACTIVE") || s.contains("TERMINAT") || s.contains("OFFBOARD") || "LEFT".equals(s));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> documentsOf(Employee e) {
        Map<String, Object> custom = e.getCustomFields();
        if (custom == null) return List.of();
        Object raw = custom.get("documents");
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) out.add(cast(m));
        }
        return out;
    }

    private static Map<String, Object> cast(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k != null) out.put(String.valueOf(k), v);
        });
        return out;
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return null;
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
