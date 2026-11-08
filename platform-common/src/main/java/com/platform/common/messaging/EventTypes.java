package com.platform.common.messaging;

public final class EventTypes {
    public static final String PAYROLL_LEAVE_COVER_COMMAND = "payroll.leave_cover.command";
    public static final String LEAVE_COVER_RESULT = "leave.cover.result";
    public static final String NOTIFICATION_CREATE = "notification.create";
    public static final String FILE_SCAN_COMMAND = "files.scan.command";
    public static final String REPORT_EXPORT_COMMAND = "reports.export.command";
    public static final String PEOPLE_EMPLOYEE_UPSERTED = "people.employee.upserted";

    private EventTypes() {
    }
}
