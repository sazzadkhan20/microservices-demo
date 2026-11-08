package com.platform.registry.infrastructure.provision;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Splits the legacy monolith settings JSON into People / Leave / Time&amp;Work ownership (guide §3).
 * Each service stores only its keys — never a cloned full blob.
 */
public final class SettingsOwnership {

    private SettingsOwnership() {}

    /** Leave-owned config keys. */
    public static final Set<String> LEAVE_KEYS = Set.of(
            "approvalAuthority",
            "leaveIdleReminder",
            "leaveRules",
            "notificationEvents" // leave-event toggles; People may also keep a copy for employee events
    );

    /** Time&amp;Work-owned config keys. */
    public static final Set<String> TIMEWORK_KEYS = Set.of(
            "scheduleRules",
            "attendanceRules",
            "workLogRule",
            "workLogRules",
            "workLogMinPercent",
            "workLogPercentOf",
            "defaultTimeZone",
            "defaultGraceMinutes",
            "defaultHalfDayHours",
            "defaultCheckoutCutoff",
            "taskRules"
    );

    /** People-owned config keys (everything else that remains after stripping Leave/Time&amp;Work). */
    public static final Set<String> PEOPLE_KEYS = Set.of(
            "businessPack",
            "employeeCodePattern",
            "employeeCodePrefix",
            "employeeCodeDigits",
            "employeeCodeMode",
            "defaultCreateRoleCodes",
            "employeeRules",
            "reportingLine",
            "checklists",
            "notifications",
            "payroll"
    );

    public static Map<String, Object> sliceLeave(Map<String, Object> full) {
        return slice(full, LEAVE_KEYS);
    }

    public static Map<String, Object> sliceTimeWork(Map<String, Object> full) {
        return slice(full, TIMEWORK_KEYS);
    }

    public static Map<String, Object> slicePeople(Map<String, Object> full) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (full == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : full.entrySet()) {
            String key = e.getKey();
            // Time&Work keys must not remain on People. Leave-only keys (except shared
            // notificationEvents) are stripped; notificationEvents stays for People UI/APIs.
            if (TIMEWORK_KEYS.contains(key)) {
                continue;
            }
            if (LEAVE_KEYS.contains(key) && !"notificationEvents".equals(key)) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    private static Map<String, Object> slice(Map<String, Object> full, Set<String> keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (full == null) {
            return out;
        }
        for (String key : keys) {
            if (full.containsKey(key)) {
                out.put(key, full.get(key));
            }
        }
        // Always stamp pack id when present so operators can see which pack seeded the slice.
        if (full.containsKey("businessPack")) {
            out.putIfAbsent("businessPack", full.get("businessPack"));
        }
        return out;
    }
}
