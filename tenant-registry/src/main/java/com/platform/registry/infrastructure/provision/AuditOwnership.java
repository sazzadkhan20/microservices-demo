package com.platform.registry.infrastructure.provision;

import java.util.Locale;
import java.util.Set;

/**
 * Maps legacy monolith {@code audit_log.entity} values to the owning microservice (guide §3).
 */
public final class AuditOwnership {

    private AuditOwnership() {}

    public static final Set<String> PEOPLE = Set.of(
            "checklist", "employeedocument", "employee", "department", "designation",
            "location", "team", "approle", "apppermission", "appuserrole",
            "extindexedattribute", "tenantfielddefinition", "reportinglinehistory",
            "employmentfieldhistory"
    );

    public static final Set<String> LEAVE = Set.of(
            "leaveapplication", "leavecategory", "leavebalances", "leaverequests",
            "leaveapproval", "leavetype", "leavepolicy", "leaveblackout",
            "leaveoperation", "leavebalance", "leavebalanceledger", "storeshiftcoverage",
            "leaveapprovaldelegate"
    );

    public static final Set<String> TIMEWORK = Set.of(
            "workschedule", "attendance_record", "attendancerecord", "holiday",
            "attendance_correction", "attendancecorrection", "worktask", "worklog",
            "worktaskassignee", "lateabsence"
    );

    public static final Set<String> PAYROLL = Set.of(
            "payslip", "salaryhistory", "payrollconfig", "payrollconfigentity",
            "finalizeoperation"
    );

    public static final Set<String> FILES = Set.of(
            "fileobject", "file", "documentaccess"
    );

    public static final Set<String> NOTIFICATION = Set.of(
            "notification", "notifications"
    );

    public static final Set<String> REPORT = Set.of(
            "reportexport", "headcount", "attendancereport"
    );

    public enum Owner {
        PEOPLE, LEAVE, TIMEWORK, PAYROLL, FILES, NOTIFICATION, REPORT, UNKNOWN
    }

    public static Owner ownerOf(String entity) {
        if (entity == null || entity.isBlank()) {
            return Owner.UNKNOWN;
        }
        String key = entity.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (PEOPLE.contains(key)) {
            return Owner.PEOPLE;
        }
        if (LEAVE.contains(key)) {
            return Owner.LEAVE;
        }
        if (TIMEWORK.contains(key)) {
            return Owner.TIMEWORK;
        }
        if (PAYROLL.contains(key)) {
            return Owner.PAYROLL;
        }
        if (FILES.contains(key)) {
            return Owner.FILES;
        }
        if (NOTIFICATION.contains(key)) {
            return Owner.NOTIFICATION;
        }
        if (REPORT.contains(key)) {
            return Owner.REPORT;
        }
        return Owner.UNKNOWN;
    }

    public static String sqlInList(Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String k : keys) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('\'').append(k.replace("'", "''")).append('\'');
        }
        return sb.toString();
    }
}
