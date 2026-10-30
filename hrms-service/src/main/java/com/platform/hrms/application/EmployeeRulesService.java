package com.platform.hrms.application;

import com.platform.hrms.extension.config.TenantConfigLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven employee business rules from {@code tenant_settings.employeeRules}.
 * New business = pack/settings change — not Java if-branches on brand names.
 */
@Service
public class EmployeeRulesService {

    public static final List<String> DEFAULT_STATUSES = List.of(
            "Active", "On leave", "Inactive", "Resigned", "Terminated");
    public static final List<String> DEFAULT_OFFBOARD = List.of("Resigned", "Terminated");
    public static final List<String> DEFAULT_INACTIVE = List.of("Inactive", "Resigned", "Terminated");
    public static final List<String> DEFAULT_CONTRACT_TYPES = List.of("CONTRACT", "CONTRACTOR", "INTERN");
    public static final List<String> DEFAULT_WORK_PATTERNS = List.of(
            "LOCATION_DEFAULT", "DAYS_5", "DAYS_6", "HOURLY");
    public static final List<String> DEFAULT_DOC_TYPES = List.of(
            "Contract", "NDA", "Agreement", "ID copy", "Certificate", "Other");
    public static final List<String> DEFAULT_SELF_EDIT = List.of(
            "phone", "personalEmail", "address", "emergencyName", "emergencyRelationship", "emergencyPhone");
    public static final List<String> DEFAULT_EMPLOYMENT_TRACKED = List.of(
            "fullName", "email", "preferredName", "jobTitle", "departmentId", "designationId",
            "managerId", "teamId", "locationId", "employmentType", "startDate", "probationEnd",
            "contractEnd", "payCurrency", "workPattern", "baseSalary", "hourlyRate", "status",
            "phone", "personalEmail", "address", "emergencyName", "emergencyRelationship",
            "emergencyPhone", "exitDate", "exitReason");

    private final TenantConfigLoader configLoader;

    public EmployeeRulesService(TenantConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Transactional(readOnly = true)
    public Rules load() {
        Map<String, Object> cfg = map(configLoader.loadSettingsMap().get("employeeRules"));
        List<String> domains = new ArrayList<>();
        String primary = str(cfg.get("workEmailDomain"), null);
        if (primary != null) {
            domains.add(primary.toLowerCase(Locale.ROOT));
        }
        for (String a : list(cfg.get("workEmailDomainAliases"), List.of())) {
            if (a != null && !a.isBlank()) {
                domains.add(a.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new Rules(
                primary,
                domains,
                list(cfg.get("employmentStatuses"), DEFAULT_STATUSES),
                list(cfg.get("offboardingStatuses"), DEFAULT_OFFBOARD),
                list(cfg.get("inactiveStatuses"), DEFAULT_INACTIVE),
                upperList(cfg.get("contractTypesRequiringEnd"), DEFAULT_CONTRACT_TYPES),
                list(cfg.get("workPatterns"), DEFAULT_WORK_PATTERNS),
                list(cfg.get("documentTypes"), DEFAULT_DOC_TYPES),
                list(cfg.get("selfEditableFields"), DEFAULT_SELF_EDIT),
                list(cfg.get("employmentHistoryFields"), DEFAULT_EMPLOYMENT_TRACKED),
                list(cfg.get("requiredForActive"), List.of(
                        "fullName", "email", "departmentId", "locationId", "startDate", "managerId")),
                bool(cfg.get("requireManagerOnCreate"), true),
                bool(cfg.get("requireOffboardingChecklist"), true),
                bool(cfg.get("requireExitDate"), true),
                bool(cfg.get("requireExitReason"), true),
                bool(cfg.get("requireReportReassignment"), true)
        );
    }

    public void assertWorkEmail(String email, Rules rules) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        List<String> domains = rules.workEmailDomains();
        if (domains == null || domains.isEmpty()) {
            return;
        }
        String lower = email.trim().toLowerCase(Locale.ROOT);
        for (String domain : domains) {
            if (domain != null && !domain.isBlank() && lower.endsWith("@" + domain.trim().toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "Work email must end with @" + String.join(" or @", domains));
    }

    public void assertStatusAllowed(String status, Rules rules) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        boolean ok = rules.employmentStatuses().stream().anyMatch(s -> s.equalsIgnoreCase(status.trim()));
        if (!ok) {
            throw new IllegalArgumentException(
                    "status must be one of: " + String.join(", ", rules.employmentStatuses()));
        }
    }

    public boolean isOffboarding(String status, Rules rules) {
        if (status == null) {
            return false;
        }
        return rules.offboardingStatuses().stream().anyMatch(s -> s.equalsIgnoreCase(status.trim()));
    }

    public boolean isInactive(String status, Rules rules) {
        if (status == null) {
            return false;
        }
        return rules.inactiveStatuses().stream().anyMatch(s -> s.equalsIgnoreCase(status.trim()));
    }

    public boolean requiresContractEnd(String employmentType, Rules rules) {
        if (employmentType == null || employmentType.isBlank()) {
            return false;
        }
        String t = employmentType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return rules.contractTypesRequiringEnd().contains(t);
    }

    public void assertDocumentType(String type, Rules rules) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("documentType is required");
        }
        boolean ok = rules.documentTypes().stream().anyMatch(t -> t.equalsIgnoreCase(type.trim()));
        if (!ok) {
            throw new IllegalArgumentException(
                    "documentType must be one of: " + String.join(", ", rules.documentTypes()));
        }
    }

    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, Object> out = new HashMap<>();
        m.forEach((k, v) -> {
            if (k != null) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
    }

    private static boolean bool(Object v, boolean def) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(v.toString().trim());
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object raw, List<String> def) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return def;
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o).trim());
            }
        }
        return out.isEmpty() ? def : out;
    }

    private static List<String> upperList(Object raw, List<String> def) {
        return list(raw, def).stream()
                .map(s -> s.toUpperCase(Locale.ROOT).replace(' ', '_'))
                .collect(Collectors.toList());
    }

    public record Rules(
            String workEmailDomain,
            List<String> workEmailDomains,
            List<String> employmentStatuses,
            List<String> offboardingStatuses,
            List<String> inactiveStatuses,
            List<String> contractTypesRequiringEnd,
            List<String> workPatterns,
            List<String> documentTypes,
            List<String> selfEditableFields,
            List<String> employmentHistoryFields,
            List<String> requiredForActive,
            boolean requireManagerOnCreate,
            boolean requireOffboardingChecklist,
            boolean requireExitDate,
            boolean requireExitReason,
            boolean requireReportReassignment
    ) {
        public Set<String> selfEditableSet() {
            return selfEditableFields.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        }
    }
}
